package art.arcane.auram.commands;

import art.arcane.auram.quests.QuestWorkspace;
import art.arcane.auram.recipes.RecipeAlterTask;
import art.arcane.auram.recipes.RecipeWorkspace;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class AuramCommands {

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("auram")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("chaos")
                        .then(Commands.literal("clear")
                                .executes(context -> runChaosClear(context.getSource()))
                        )
                        .then(Commands.literal("gen")
                                .then(Commands.literal("quests")
                                        .executes(context -> runQuestGen(context.getSource()))
                                )
                                .then(Commands.argument("difficulty", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> {
                                            int difficulty = IntegerArgumentType.getInteger(context, "difficulty");
                                            return runChaosGeneration(context.getSource(), difficulty);
                                        })
                                )
                        )
                )
        );
    }

    private static int runQuestGen(CommandSourceStack source) {
        Thread thread = new Thread(() -> {
            try {
                source.sendSuccess(() -> Component.literal("§aStarting Chaos Quest Generation..."), true);
                QuestWorkspace q = new QuestWorkspace();
                q.generate(source);
                source.getServer().execute(() -> {
                    source.getServer().getCommands().performPrefixedCommand(source, "ftbquests reload");
                    source.sendSystemMessage(Component.literal("§aChaos Quest Generation completed!"));
                });

            } catch (Exception e) {
                source.sendFailure(Component.literal("§cAn error occurred during Chaos Quest Generation: " + e.getMessage()));
                e.printStackTrace();
            }
        }, "Chaos Generator");
        thread.start();
        return 1;
    }

    private static int runChaosClear(CommandSourceStack source) {
        try{
            net.minecraft.server.MinecraftServer server = source.getServer();
            Path packDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                    .resolve("auram_chaos_pack");
            if(Files.exists(packDir)) {
                Files.walk(packDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
            source.getServer().execute(() -> {
                source.getServer().getCommands().performPrefixedCommand(source, "reload");
                source.sendSystemMessage(Component.literal("§aCleared Chaos"));
            });
        }catch(Exception e) {
            source.sendSystemMessage(Component.literal("§cFailed to Clear Chaos (" + e.getMessage() + ")"));
            e.printStackTrace();
        }
        
        return 1;
    }

    private static int runChaosGeneration(CommandSourceStack source, int difficulty) {
        Thread thread = new Thread(() -> {
            try {
                Level level = source.getLevel();
                source.sendSuccess(() -> Component.literal("§aStarting Chaos Generation (Difficulty: " + difficulty + ")..."), true);

                RecipeWorkspace workspace = new RecipeWorkspace();
                long time = System.currentTimeMillis();
                RecipeAlterTask rat = new RecipeAlterTask(workspace, source, System.currentTimeMillis(), difficulty);
                rat.execute();

                workspace.exportToDataPack(source.getLevel());
                source.getServer().execute(() -> {
                    source.getServer().getCommands().performPrefixedCommand(source, "reload");
                    source.sendSystemMessage(Component.literal("§aChaos Generation completed in " + (System.currentTimeMillis() - time) / 1000.0 + " seconds!"));
                });

            } catch (Exception e) {
                source.sendFailure(Component.literal("§cAn error occurred during Chaos Generation: " + e.getMessage()));
                e.printStackTrace();
            }
        }, "Chaos Generator");
        thread.start();
        return 1;
    }
}