package art.arcane.auram.recipes;

public abstract class RecipeModifier {
    public final String name;
    public final int minDifficulty;
    
    public RecipeModifier(String name, int minDifficulty) {
        this.name = name;
        this.minDifficulty = minDifficulty;
    }
    
    public abstract void init(ChaosContext context);
    
    public abstract boolean evaluate(ChaosContext context);
    
    public abstract int apply(ChaosContext context);
    
    public int doApply(ChaosContext context) {
        if(context.difficulty >= minDifficulty && evaluate(context)) {
            return apply(context);
        }
        
        return 0;
    }
}
