package art.arcane.auram.recipes;

public class IngredientFilter {
    public Boolean tagged = null;
    public Boolean multipleItems = null;
    public Double percentLower = 0.9;
    public Double percentHigher = 1.1;
    
    public IngredientFilter() {}
    
    public IngredientFilter tagged(Boolean tagged) {
        this.tagged = tagged;
        return this;
    }
    
    public IngredientFilter multipleItems(Boolean multipleItems) {
        this.multipleItems = multipleItems;
        return this;
    }
    
    public IngredientFilter percentLower(Double percentLower) {
        this.percentLower = percentLower;
        return this;
    }
    
    public IngredientFilter percentHigher(Double percentHigher) {
        this.percentHigher = percentHigher;
        return this;
    }
}
