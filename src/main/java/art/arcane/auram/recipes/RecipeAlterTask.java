package art.arcane.auram.recipes;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RecipeAlterTask {
    public final RecipeWorkspace workspace;
    public final long seed;
    public final int difficulty;
    public final Random[] randoms;
    public final ExecutorService[] executors;
    public final ChaosContext[] contexts;
    public final CommandSourceStack source;
    
    public RecipeAlterTask(RecipeWorkspace workspace, CommandSourceStack stack, long seed, int difficulty) {
        this.workspace = workspace;
        this.seed = seed;
        this.difficulty = difficulty;
        this.source = stack;
        int threads = Runtime.getRuntime().availableProcessors();
        this.executors = new ExecutorService[threads];
        this.randoms = new Random[threads];
        this.contexts = new ChaosContext[threads];
        
        for(int i = 0; i < threads; i++) {
            executors[i] = Executors.newFixedThreadPool(1);
            randoms[i] = new Random(seed ^ ((i+602) * 1291L));
            contexts[i] = new ChaosContext(workspace, source, randoms[i], difficulty);
        }
    }
    
    public void log(String message) {
        source.sendSystemMessage(Component.literal("[Chaos]: " + message));
    }
    
    public void execute() {
        log("Loading Recipes. This may take a while...");
        workspace.loadFromGameParallel(source.getLevel());
        log("Loaded " + workspace.recipeMap.size() + " recipes.");
        Random baseRandom = new Random(seed);
        ChaosContext baseContext = new ChaosContext(workspace, source, baseRandom, difficulty);
        
        for(RecipeModifier i : workspace.modifiers) {
            i.init(baseContext);
        }

        int r = 0;
        int t = workspace.recipeMap.size();
        AtomicInteger completedTaskCount = new AtomicInteger(0);
        AtomicInteger totalModifications = new AtomicInteger(0);
        AtomicLong lTime = new AtomicLong(System.currentTimeMillis());
        int totalTaskCount = t * workspace.modifiers.size();
        for (VirtualRecipe recipe : workspace.recipeMap.values()) {
            final VirtualRecipe virtualRecipe = recipe;
            final int fr = r++;
            executors[fr % executors.length].submit(() -> {
                ChaosContext context = contexts[fr % contexts.length];
                context.recipe = virtualRecipe;

                for (RecipeModifier modifier : workspace.modifiers) {
                    completedTaskCount.incrementAndGet();
                    int m = modifier.doApply(context);

                    if(m > 0) {
                        totalModifications.addAndGet(m);
                    }
                }
                
                if(System.currentTimeMillis() - lTime.get() > 3000) {
                    lTime.set(System.currentTimeMillis());
                    int completed = completedTaskCount.get();
                    log(String.format("%.2f%% (%d/%d) Modifications so far: %d", (completed * 100.0) / totalTaskCount, completed, totalTaskCount, totalModifications.get()));
                }
            });
        }
        
        for(ExecutorService executor : executors) {
            executor.shutdown();
        }
        
        for(ExecutorService executor : executors) {
            try {
                if(!executor.awaitTermination(10, java.util.concurrent.TimeUnit.DAYS)){
                    for(Runnable rtask : executor.shutdownNow()) {
                        rtask.run();
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        
        log("Completed " + totalModifications.get() + " modification tasks.");
    }
}
