package art.arcane.auram;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class AuramConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ForgeConfigSpec.ConfigValue<String> llmBaseUrl;
        public final ForgeConfigSpec.ConfigValue<String> llmModel;
        public final ForgeConfigSpec.ConfigValue<String> llmKey;
        public final ForgeConfigSpec.IntValue defaultChaosDifficulty;
        public final ForgeConfigSpec.DoubleValue varianceMultiplier;
        public final ForgeConfigSpec.BooleanValue debugMode;
        public final ForgeConfigSpec.BooleanValue questsUseLLM;
        public final ForgeConfigSpec.IntValue questThreads;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("LLM Config");
            llmBaseUrl = builder
                    .comment("Base URL for OpenAI API requests. Change this if using a proxy or custom endpoint. For ollama use 'http://localhost:11434/v1/'. Or for openai use 'https://api.openai.com/v1/'.")
                    .define("llmBaseUrl", "http://localhost:11434/v1/");
            llmModel = builder
                    .comment("The AI model to use for various text generation tasks. It is recommended to use local models!")
                    .define("llmModel", "gpt-oss-20b");
            llmKey = builder
                    .comment("API key for OpenAI requests. Not needed for local models like ollama or llama.cpp.")
                    .define("llmKey", "ollama");
            
            builder.pop();
            
            builder.push("Chaos Generation");
            
            questsUseLLM = builder
                    .comment("If true, quests will use the LLM to help generate quest text and objectives.")
                    .define("questsUseLLM",  false);
            
            questThreads = builder
                    .comment("Number of threads to use for quest generation tasks. Set to 0 to use available processors.")
                    .defineInRange("questThreads", 0, 0, 64);
            
            defaultChaosDifficulty = builder
                    .comment("The default difficulty level (0-100) if none is specified in the command.")
                    .defineInRange("defaultDifficulty", 50, 0, 100);

            varianceMultiplier = builder
                    .comment("Multiplier for how much EMC variance is allowed per difficulty point.")
                    .defineInRange("varianceMultiplier", 1.9, 0.1, 5.0);

            debugMode = builder
                    .comment("Enable verbose logging to console during chaos generation.")
                    .define("debugMode", false);

            builder.pop();
        }
    }
}