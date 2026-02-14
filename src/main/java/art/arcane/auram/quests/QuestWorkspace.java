package art.arcane.auram.quests;

import art.arcane.auram.AuramConfig;
import art.arcane.auram.quests.rewards.QRewardItem;
import art.arcane.auram.quests.rewards.QRewardRandom;
import art.arcane.auram.quests.rewards.QRewardXP;
import art.arcane.auram.quests.tasks.QTaskItem;
import art.arcane.auram.quests.tasks.QTaskKill;
import art.arcane.auram.util.EMCHelper;
import art.arcane.auram.util.OllamaClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QuestWorkspace {
    public static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("ftbquests");
    public static final File QUESTS_DIR = CONFIG_DIR.resolve("quests").toFile();
    public static final File CHAPTERS_DIR = new File(QUESTS_DIR, "chapters");
    public static final Gson GSON = new Gson();
    public static long time = System.currentTimeMillis();
    public static OllamaClient llm;
    public static final String[] shapes = {
            "circle",
            "square",
            "rounded_square",
            "diamond",
            "pentagon",
            "hexagon",
            "octagon",
            "heart",
            "gear"
    };
    
    public static void deleteChapters() throws IOException {
        if (CHAPTERS_DIR.exists()) {
            for (File file : Objects.requireNonNull(CHAPTERS_DIR.listFiles())) {
                Files.delete(file.toPath());
            }
        }
    }
    
    public static void save(QuestPool pool) throws IOException {
        saveChapterGroups(pool.chapterGroups);

        try{
            if(!new File(QUESTS_DIR, "chapters").exists()) {
                new File(QUESTS_DIR, "chapters").mkdirs();
            }

            if(!new File(QUESTS_DIR, "reward_tables").exists()) {
                new File(QUESTS_DIR, "reward_tables").mkdirs();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        try{
            System.out.println("Saving " + pool.chapters.size() + " chapters and " + pool.rewardTables.size() + " reward tables.");
            for(QChapter c : pool.chapters) {
                saveChapter(c);
                System.out.println("Saved chapter: " + c.fileName);
            }

            for(QRewardTable rt : pool.rewardTables) {
                File file = new File(QUESTS_DIR, "reward_tables" + File.separator + rt.crateId + ".snbt");
                Files.writeString(file.toPath(), rt.toNBT().toString());
                System.out.println("Saved reward table: " + rt.crateId);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void saveChapterGroups(List<QChapterGroup> groups) throws IOException {
        CompoundTag tag = new CompoundTag();
        ListTag ch = new ListTag();
        for(QChapterGroup g : groups) {
            ch.add(g.toNBT());
        }
        
        tag.put("chapter_groups", ch);
        File file = new File(QUESTS_DIR, "chapter_groups.snbt");
        Files.writeString(file.toPath(), tag.toString());
        System.out.println("Saved chapter groups: " + groups.size());
    }
    
    public static void saveChapter(QChapter chapter) throws IOException {
        File file = new File(CHAPTERS_DIR, chapter.fileName + ".snbt");
        Files.writeString(file.toPath(), chapter.toNBT().toString());
    }

    public void generate(CommandSourceStack source) throws IOException {
        int th = AuramConfig.COMMON.questThreads.get();
        th = th <= 0 ? Runtime.getRuntime().availableProcessors() : th;
        ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(th);
        QuestPool pool = new QuestPool();
        ServerLevel level = source.getLevel();
        Map<String, Set<Recipe<?>>> modRecipes = new HashMap<>();
        
        try {
            llm = new OllamaClient(AuramConfig.COMMON.llmBaseUrl.get(), AuramConfig.COMMON.llmModel.get());
        } catch (Exception e) {
            System.err.println("Failed to initialize OllamaClient: " + e.getMessage());
            e.printStackTrace();
        }
        
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            modRecipes.computeIfAbsent(recipe.getId().getNamespace(), k -> new HashSet<>()).add(recipe);
        }
        
        QChapterGroup groupMods = new QChapterGroup("Mods");
        pool.chapterGroups.add(groupMods);
        
        for(String i : modRecipes.keySet()) {
            executor.submit(() -> {
                try{
                    System.out.println("Building chapter/rewards build for mod: " + i);
                    QChapter chapter = new QChapter("chaos_mods_" + i);
                    chapter.group = groupMods.id;
                    chapter.title = getModName(i);
                    QRewardTable rewards = new QRewardTable();
                    buildModChapter(rewards, source, i, modRecipes.get(i), chapter, level, (int)(Math.max(1, Math.min(10, Math.ceil(modRecipes.get(i).size()/200.0)))));
                    synchronized (pool){
                        pool.chapters.add(chapter);
                        pool.rewardTables.add(rewards);
                    }
                }
                
                catch(Exception e) {
                    System.err.println("Error building chapter/rewards for mod: " + i + " - " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
        
        QRewardTable rewards = new QRewardTable();
        rewards.crateColor = 0xCC00FF;
        rewards.crateName = "Loot Box";
        rewards.crateId = "loot_box";
        rewards.crateDropsBoss = 1;
        rewards.emptyWeight = 0f;
        rewards.title = "Rewards";
        rewards.lootSize = 1;
        
        synchronized (pool.rewardTables) {
            pool.rewardTables.add(rewards);
        }

        QChapterGroup groupDiscovery = new QChapterGroup("Discovery");
        pool.chapterGroups.add(groupDiscovery);
        
        executor.submit(() -> {
            QChapter chapter = new QChapter("chaos_discovery_slayer");
            chapter.title = "Slayer";
            chapter.group = groupDiscovery.id;
            chapter.icon = BuiltInRegistries.ITEM.get(ResourceLocation.tryBuild("minecraft", "iron_sword"));
            buildKillQuests(rewards, chapter);
            synchronized (pool){
                pool.chapters.add(chapter);
            }
        });

        executor.shutdown();

        while (!executor.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }


        for(QRewardTable rt : pool.rewardTables) {
            ItemStack is = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryBuild("ftbquests", "lootcrate")));
            CompoundTag ct = new CompoundTag();
            ct.putString("type", rt.crateId);
            is.setTag(ct);
            rewards.nbtRewards.put(is,1f);
        }

        save(pool);
    }
    
    private void buildKillQuests(QRewardTable rewards, QChapter chapter) {
        double x = 0;
        double y = 0;
        
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            if(type.canSummon() && (type.getCategory() == MobCategory.MONSTER ||
                    type.getCategory() == MobCategory.UNDERGROUND_WATER_CREATURE ||
                    type.getCategory() == MobCategory.WATER_CREATURE
                    || type.getCategory() == MobCategory.CREATURE
                    || type.getCategory() == MobCategory.AXOLOTLS
                    )) {
                ResourceLocation registryName = ForgeRegistries.ENTITY_TYPES.getKey(type);

                if (registryName != null) {
                    QQuest a = buildKillQuest(x, y+1, rewards, registryName, 1, "circle");
                    QQuest b = buildKillQuest(x, y+2, rewards, registryName, 3+Math.round(Math.random() * 5), "diamond");
                    QQuest c = buildKillQuest(x, y+3, rewards, registryName, 12+Math.round(Math.random() * 16), "pentagon");
                    QQuest d = buildKillQuest(x, y+4, rewards, registryName, 36+Math.round(Math.random() * 40), "hexagon");
                    QQuest e = buildKillQuest(x, y+5, rewards, registryName, 100+Math.round(Math.random() * 129), "octagon");
                    e.dependencies.add(d.id);
                    d.dependencies.add(c.id);
                    c.dependencies.add(b.id);
                    b.dependencies.add(a.id);
                    synchronized (chapter.quests){
                        chapter.quests.add(a);
                        chapter.quests.add(b);
                        chapter.quests.add(c);
                        chapter.quests.add(d);
                        chapter.quests.add(e);
                    }
                    x++;
                }
            }
        }
    }
    
    private QQuest buildKillQuest(double x, double y, QRewardTable rewards, ResourceLocation location, long threshold, String shape) {
        QQuest quest = new QQuest(x,y);
        quest.tasks.add(new QTaskKill(location, threshold));
        quest.rewards.add(new QRewardXP(5+(int)(threshold / 3)));
        
        if(threshold > 1) {
            quest.rewards.add(new QRewardRandom(rewards.id));
        }
        
        quest.hideUntilDepsComplete = true;
        quest.invisible = true;
        quest.hideDetailsUntilStartable = true;
        quest.invisibleUntilTasks = 1;
        quest.shape = shape;
        
        return quest;
    }

    private void buildModChapter(QRewardTable rewards, CommandSourceStack source, String modId, Set<Recipe<?>> recipes, QChapter chapter, ServerLevel level, int mergeFactor) {
        System.out.println("Building chapter for mod: " + modId + " with " + recipes.size() + " recipes.");
        Map<Item, Recipe<?>> canonicalRecipes = new HashMap<>();
        for (Recipe<?> r : recipes) {
            Item outputItem = r.getResultItem(level.registryAccess()).getItem();
            canonicalRecipes.putIfAbsent(outputItem, r);
        }
        List<Recipe<?>> uniqueRecipes = new ArrayList<>(canonicalRecipes.values());
        Map<ResourceLocation, Recipe<?>> idToRecipe = new HashMap<>();
        Map<Item, ResourceLocation> itemToRecipeId = new HashMap<>();
        for (Recipe<?> r : uniqueRecipes) {
            idToRecipe.put(r.getId(), r);
            itemToRecipeId.put(r.getResultItem(level.registryAccess()).getItem(), r.getId());
        }
        Map<ResourceLocation, List<ResourceLocation>> rawTree = new HashMap<>();
        for (Recipe<?> r : uniqueRecipes) {
            Set<ResourceLocation> dependencies = new HashSet<>();
            for (Ingredient ingredient : r.getIngredients()) {
                Arrays.stream(ingredient.getItems())
                        .filter(s -> !s.isEmpty())
                        .map(ItemStack::getItem)
                        .distinct()
                        .forEach(inputItem -> {
                            ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(inputItem);
                            if (!inputId.getNamespace().equals(modId)) return;
                            if (itemToRecipeId.containsKey(inputItem)) {
                                ResourceLocation depId = itemToRecipeId.get(inputItem);
                                if (!depId.equals(r.getId())) dependencies.add(depId);
                            }
                        });
            }
            rawTree.put(r.getId(), new ArrayList<>(dependencies));
        }

        Map<ResourceLocation, Integer> recipeDepths = new HashMap<>();
        Queue<ResourceLocation> queue = new LinkedList<>();
        Set<ResourceLocation> allChildren = rawTree.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        for (Recipe<?> r : uniqueRecipes) {
            if (!allChildren.contains(r.getId())) {
                recipeDepths.put(r.getId(), 0);
                queue.add(r.getId());
            }
        }

        if (queue.isEmpty() && !uniqueRecipes.isEmpty()) {
            ResourceLocation fallback = uniqueRecipes.get(0).getId();
            recipeDepths.put(fallback, 0);
            queue.add(fallback);
        }

        int safetyCounter = 0;
        int maxOps = uniqueRecipes.size() * 50;

        while (!queue.isEmpty() && safetyCounter++ < maxOps) {
            ResourceLocation current = queue.poll();
            int currentDepth = recipeDepths.get(current);

            List<ResourceLocation> children = rawTree.get(current);
            if (children != null) {
                for (ResourceLocation child : children) {
                    int newDepth = currentDepth + 1;
                    if (newDepth > recipeDepths.getOrDefault(child, -1)) {
                        recipeDepths.put(child, newDepth);
                        queue.add(child);
                    }
                }
            }
        }
        
        List<Item> allItems = uniqueRecipes.stream()
                .map(i -> i.getResultItem(level.registryAccess()).getItem()).distinct()
                .filter(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(modId))
                .sorted(Comparator.comparingLong(EMCHelper::getEmcValue))
                .toList();
         
        rewards.crateColor = 0xFFFFFF;
        rewards.crateName = "Box of " + getModName(modId);
        rewards.crateId = "chaos_box_" + modId;
        rewards.crateDropsBoss = 1;
        rewards.emptyWeight = 0f;
        rewards.icon = allItems.stream().findFirst().orElse(null);
        rewards.title = getModName(modId) + " Rewards";
        rewards.lootSize = 1;
        
        float nex = 3f;
        for(Item i : allItems) {
            rewards.rewards.put(i, nex+=1f);
        }
        
        int maxDepth = -1;
        Recipe<?> anyMaxedDepth = null;
        Map<Integer, List<Recipe<?>>> recipesByDepth = new TreeMap<>();
        for (Recipe<?> r : uniqueRecipes) {
            int d = recipeDepths.getOrDefault(r.getId(), 0);
            if(d > maxDepth) {
                maxDepth = d;
                anyMaxedDepth = r;
            }
            recipesByDepth.computeIfAbsent(d, k -> new ArrayList<>()).add(r);
        }
        
        if(maxDepth > -1) {
            chapter.icon = anyMaxedDepth.getResultItem(level.registryAccess()).getItem();
        }

        List<MergedNode> allNodes = new ArrayList<>();
        Map<ResourceLocation, MergedNode> recipeIdToNodeMap = new HashMap<>();

        for (Map.Entry<Integer, List<Recipe<?>>> entry : recipesByDepth.entrySet()) {
            int depth = entry.getKey();
            List<Recipe<?>> layerRecipes = entry.getValue();
            layerRecipes.sort(Comparator.comparing(r -> r.getResultItem(level.registryAccess()).getHoverName().getString()));

            for (int i = 0; i < layerRecipes.size(); i++) {
                Recipe<?> r = layerRecipes.get(i);
                int batchIndex = i / mergeFactor;
                String nodeId = "depth_" + depth + "_group_" + batchIndex;

                MergedNode node;
                if (i % mergeFactor == 0) {
                    node = new MergedNode(nodeId, depth);
                    allNodes.add(node);
                } else {
                    node = allNodes.get(allNodes.size() - 1);
                }

                node.recipes.add(r);
                recipeIdToNodeMap.put(r.getId(), node);
            }
        }

        Map<ResourceLocation, List<ResourceLocation>> metaTree = new HashMap<>();
        for (MergedNode node : allNodes) {
            Set<ResourceLocation> nodeDependencies = new HashSet<>();
            ResourceLocation nodeRes = ResourceLocation.tryBuild(modId, node.id);

            for (Recipe<?> r : node.recipes) {
                List<ResourceLocation> rawParents = rawTree.get(r.getId());
                if (rawParents != null) {
                    for (ResourceLocation rawParentId : rawParents) {
                        MergedNode parentNode = recipeIdToNodeMap.get(rawParentId);
                        if (parentNode != null && !parentNode.id.equals(node.id)) {
                            nodeDependencies.add(ResourceLocation.tryBuild(modId, parentNode.id));
                        }
                    }
                }
            }
            metaTree.put(nodeRes, new ArrayList<>(nodeDependencies));
        }

        Set<ResourceLocation> metaChildren = metaTree.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        List<ResourceLocation> metaRoots = allNodes.stream()
                .map(n -> ResourceLocation.tryBuild(modId, n.id))
                .filter(id -> !metaChildren.contains(id))
                .collect(Collectors.toList());
        QuestLayout layout = new QuestLayout(0, 10);
        layout.calculate(metaRoots, metaTree);
        Map<String, QQuest> createdQuests = new HashMap<>();
        int t = layout.positions.size();
        AtomicInteger c = new AtomicInteger();
        int th = AuramConfig.COMMON.questThreads.get();
        th = th <= 0 ? Runtime.getRuntime().availableProcessors() : th;
         
        layout.positions.forEach((nodeRes, pos) -> {
           
                MergedNode node = allNodes.stream().filter(n -> n.id.equals(nodeRes.getPath())).findFirst().orElse(null);
                if (node == null) return;

                QQuest quest = new QQuest(pos.x, pos.y);
                for (Recipe<?> r : node.recipes) {
                    ItemStack result = new ItemStack(r.getResultItem(level.registryAccess()).getItem(), 1);
                    quest.tasks.add(new QTaskItem(result, false));
                }
                
                quest.rewards.add(new QRewardXP(1+(int)Math.round((Math.random() * 10) * node.recipes.size())));
                
                //quest.rewards.add(new QRewardRandom(rewards.id));
                ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryBuild("ftbquests", "lootcrate")));
                CompoundTag tag = new CompoundTag();
                tag.putString("type", rewards.crateId);
                item.setTag(tag);
                quest.rewards.add(new QRewardItem(item));
                createdQuests.put(node.id, quest);
                quest.hideUntilDepsComplete = true;
                quest.hideDependencyLines = true;
                quest.invisible = true;
                quest.hideDetailsUntilStartable = true;
                quest.invisibleUntilTasks = 1;
                quest.shape = shapes[(node.depth) % shapes.length];
                
               
                if(AuramConfig.COMMON.questsUseLLM.get()) {
                    String out;
                    while(true) {
                        String p = """
You are creating a quest in modded Minecraft. 

The mod being focused on is called "%s". Here is a brief description of the mod to help you understand its theme and content:
%s

In this quest, the player must aquire the following items through crafting: 
%s

* Be adventurous and creative with the titles and descriptions.

Based on the ingredients, please provide the following information in JSON format:
{
    "title": "<THE SHORT QUEST TITLE>",
    "subtitle": "<A ONE SENTENCE SUBTITLE>",
    "description": [
        "A MULTI LINE",
        "DESCRIPTION"
    ]
}

NOTE: A java mod is directly feeding your response into a json parser. Do not include any extra commentary or text outside of the JSON structure, and dont make a markdown code block. Just pure json.
                    """;

                        p = String.format(p,
                                getModName(modId),
                                getModDescription(modId),
                                node.recipes.stream().map(r -> {
                                    ItemStack result = new ItemStack(r.getResultItem(level.registryAccess()).getItem(), 1);
                                    return "- " + result.getHoverName().getString() + " from the " + getModName(r.getId().getNamespace()) + " mod";
                                }).collect(Collectors.joining("\n")));
                        out = llm.generateResponse(p).replaceAll("```json", "").replaceAll("```", "");

                        try{
                            JsonObject json = GSON.fromJson(out, JsonObject.class);

                            if(json.has("title")) {
                                quest.title = json.get("title").getAsString();
                            }

                            if(json.has("subtitle")) {
                                quest.subtitle = json.get("subtitle").getAsString();
                            }

                            if(json.has("description")) {
                                List<String> desc = new ArrayList<>();
                                for(var el : json.getAsJsonArray("description")) {
                                    desc.add(el.getAsString());
                                }
                                quest.description = desc;
                            }


                            c.getAndIncrement();
                            if(System.currentTimeMillis() - time > 3000) {
                                source.sendSystemMessage(Component.literal("⏱ Generating " + chapter.title + " (" + c + " / " + t + " quests)"));
                                time = System.currentTimeMillis();
                            }

                            break;
                        }

                        catch(Exception e) {
                            System.out.println("⚠ Failed to parse LLM output for quest generation: " + out + " (retrying) " + e.getMessage());
                        }
                    }
                }

            chapter.quests.add(quest);

            }); 

        Map<String, Set<String>> addedEdges = new HashMap<>();
        createdQuests.forEach((nodeId, quest) -> {
            List<ResourceLocation> parents = metaTree.get(ResourceLocation.tryBuild(modId, nodeId));
            if (parents != null) {
                for (ResourceLocation parentRes : parents) {
                    QQuest parentQuest = createdQuests.get(parentRes.getPath());
                    if (parentQuest == null) continue;
                    if (createsCycle(quest.id, parentQuest.id, addedEdges)) continue;
                    quest.dependencies.add(parentQuest.id);
                    addedEdges.computeIfAbsent(quest.id, k -> new HashSet<>()).add(parentQuest.id);
                }
            }
        });
    }

    private void calculateDepthRecursive(ResourceLocation current, Map<ResourceLocation, List<ResourceLocation>> tree, int depth, Map<ResourceLocation, Integer> depths, Set<ResourceLocation> visited) {
        if (visited.contains(current)) return;
        visited.add(current);
        depths.merge(current, depth, Math::max);

        List<ResourceLocation> children = tree.getOrDefault(current, Collections.emptyList());
        for (ResourceLocation child : children) {
            calculateDepthRecursive(child, tree, depth + 1, depths, visited);
        }
        visited.remove(current);
    }

    private boolean createsCycle(String questId, String dependencyId, Map<String, Set<String>> graph) {
        return canReach(dependencyId, questId, graph, new HashSet<>());
    }

    private boolean canReach(String current, String target, Map<String, Set<String>> graph, Set<String> visited) {
        if (current.equals(target)) return true;
        if (visited.contains(current)) return false;
        visited.add(current);

        if (graph.containsKey(current)) {
            for (String neighbor : graph.get(current)) {
                if (canReach(neighbor, target, graph, visited)) return true;
            }
        }
        return false;
    }
    
    private void buildModChapterSingle(String modId, Set<Recipe<?>> recipes, QChapter chapter, ServerLevel level) {
        Map<Item, Recipe<?>> canonicalRecipes = new HashMap<>();

        for (Recipe<?> r : recipes) {
            Item outputItem = r.getResultItem(level.registryAccess()).getItem();
            canonicalRecipes.putIfAbsent(outputItem, r);
        }

        Collection<Recipe<?>> uniqueRecipes = canonicalRecipes.values();
        Map<ResourceLocation, Recipe<?>> idToRecipe = new HashMap<>();
        Map<Item, ResourceLocation> itemToRecipeId = new HashMap<>();

        for (Recipe<?> r : uniqueRecipes) {
            idToRecipe.put(r.getId(), r);
            Item outputItem = r.getResultItem(level.registryAccess()).getItem();
            itemToRecipeId.put(outputItem, r.getId());
        }

        Map<ResourceLocation, List<ResourceLocation>> recipeTree = new HashMap<>();

        for (Recipe<?> r : uniqueRecipes) {
            Set<ResourceLocation> dependencies = new HashSet<>();

            for (Ingredient ingredient : r.getIngredients()) {
                Arrays.stream(ingredient.getItems())
                        .filter(stack -> !stack.isEmpty())
                        .map(ItemStack::getItem) // Look at the Item, not the specific stack count
                        .distinct()
                        .forEach(inputItem -> {
                            ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(inputItem);
                            if (!inputId.getNamespace().equals(modId)) return;

                            if (itemToRecipeId.containsKey(inputItem)) {
                                ResourceLocation dependencyRecipeId = itemToRecipeId.get(inputItem);
                                if (!dependencyRecipeId.equals(r.getId())) {
                                    dependencies.add(dependencyRecipeId);
                                }
                            }
                        });
            }
            recipeTree.put(r.getId(), new ArrayList<>(dependencies));
        }

        Set<Item> usedAsIngredients = uniqueRecipes.stream()
                .flatMap(r -> r.getIngredients().stream())
                .flatMap(i -> Arrays.stream(i.getItems()))
                .map(ItemStack::getItem)
                .collect(Collectors.toSet());

        List<ResourceLocation> roots = uniqueRecipes.stream()
                .filter(r -> {
                    Item output = r.getResultItem(level.registryAccess()).getItem();
                    return !usedAsIngredients.contains(output);
                })
                .map(Recipe::getId)
                .collect(Collectors.toList());

        if (roots.isEmpty() && !uniqueRecipes.isEmpty()) {
            roots.add(uniqueRecipes.iterator().next().getId());
        }

        QuestLayout layout = new QuestLayout(0, 10); // Standard grid size
        layout.calculate(roots, recipeTree);
        Map<ResourceLocation, QQuest> createdQuests = new HashMap<>();

        layout.positions.forEach((recipeId, pos) -> {
            Recipe<?> recipe = idToRecipe.get(recipeId);
            if (recipe == null) return;
            QQuest quest = new QQuest(pos.x, pos.y);
            Item resultItem = recipe.getResultItem(level.registryAccess()).getItem();
            ItemStack singleResult = new ItemStack(resultItem, 1);
            quest.tasks.add(new QTaskItem(singleResult, false));
            quest.rewards.add(new QRewardXP(5));
            createdQuests.put(recipeId, quest);
            chapter.quests.add(quest);
        });

        Map<String, Set<String>> addedDependencies = new HashMap<>();

        createdQuests.forEach((recipeId, quest) -> {
            List<ResourceLocation> parents = recipeTree.get(recipeId);
            if (parents != null) {
                for (ResourceLocation parentId : parents) {
                    QQuest parentQuest = createdQuests.get(parentId);
                    if (parentQuest == null) continue;

                    if (createsCycle(quest.id, parentQuest.id, addedDependencies)) {
                        System.out.println("⚠ SKIPPED CYCLE: " + recipeId + " depends on " + parentId);
                        continue;
                    }

                    quest.dependencies.add(parentQuest.id);
                    addedDependencies.computeIfAbsent(quest.id, k -> new HashSet<>()).add(parentQuest.id);
                }
            }
        });
    }


    public void printAllTrees(Map<ResourceLocation, List<ResourceLocation>> tree) {
        tree.forEach((root, children) -> {
            if (!children.isEmpty()) {
                System.out.println(root);
                printRecursive(tree, children, "", new HashSet<>(Collections.singleton(root)));
                System.out.println(); // Space between trees
            }
        });
    }

    private void printRecursive(Map<ResourceLocation, List<ResourceLocation>> tree, List<ResourceLocation> children, String prefix, Set<ResourceLocation> visited) {
        if (children == null) return;
        for (int i = 0; i < children.size(); i++) {
            ResourceLocation child = children.get(i);
            boolean isLast = i == children.size() - 1;
            boolean isCycle = visited.contains(child);

            System.out.println(prefix + (isLast ? "└── " : "├── ") + child + (isCycle ? " [CYCLE]" : ""));

            if (!isCycle && tree.containsKey(child)) {
                Set<ResourceLocation> nextVisited = new HashSet<>(visited);
                nextVisited.add(child);
                printRecursive(tree, tree.get(child), prefix + (isLast ? "    " : "│   "), nextVisited);
            }
        }
    }

    public static String getModName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }

        Optional<? extends net.minecraftforge.fml.ModContainer> container = ModList.get().getModContainerById(namespace);

        if (container.isPresent()) {
            IModInfo modInfo = container.get().getModInfo();
            return modInfo.getDisplayName().replaceAll("&", "\\&");
        }

        return capitalize(namespace);
    }

    public static String getModDescription(String namespace) {
        if (namespace.equals("minecraft")) {
            return "The base game of Minecraft. You already know about it!";
        }

        Optional<? extends net.minecraftforge.fml.ModContainer> container = ModList.get().getModContainerById(namespace);

        if (container.isPresent()) {
            IModInfo modInfo = container.get().getModInfo();
            return modInfo.getDescription();
        }

        return capitalize(namespace);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
