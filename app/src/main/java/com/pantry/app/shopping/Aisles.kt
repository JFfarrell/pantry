package com.pantry.app.shopping

import com.pantry.app.domain.IngredientParser
import com.pantry.app.domain.TextMatch

/** Groups the list the way a supermarket is laid out, so you walk it once. */
object Aisles {

    const val PRODUCE = "Fruit & veg"
    const val MEAT_FISH = "Meat & fish"
    const val DAIRY = "Dairy & eggs"
    const val BAKERY = "Bakery"
    const val TINS = "Tins, jars & sauces"
    const val CUPBOARD = "Store cupboard"
    const val HERBS = "Herbs & spices"
    const val FROZEN = "Frozen"
    const val DRINKS = "Drinks"
    const val OTHER = "Other"

    /** Walking order in a typical UK supermarket. */
    val order = listOf(PRODUCE, BAKERY, MEAT_FISH, DAIRY, TINS, CUPBOARD, HERBS, FROZEN, DRINKS, OTHER)

    private val keywords: List<Pair<String, String>> = listOf(
        PRODUCE to "onion,shallot,garlic,leek,carrot,potato,parsnip,swede,turnip,celeriac,tomato,pepper,chilli,courgette,aubergine,mushroom,broccoli,cauliflower,cabbage,kale,spinach,rocket,lettuce,salad,cucumber,celery,bean,pea,sweetcorn,squash,pumpkin,beetroot,asparagus,sprout,ginger,lemon,lime,orange,apple,banana,avocado,berry,strawberry,raspberry,blueberry,mango,pear,plum,grape,rhubarb,watercress,samphire,radish,fennel,chicory,spring green",
        MEAT_FISH to "chicken,beef,mince,steak,lamb,pork,bacon,sausage,chorizo,ham,turkey,duck,salmon,tuna,cod,haddock,prawn,mackerel,anchovy,sardine,fish,mussel,squid,liver,brisket,rib,fillet",
        DAIRY to "milk,cream,creme fraiche,yoghurt,yogurt,butter,cheese,cheddar,parmesan,mozzarella,feta,halloumi,ricotta,mascarpone,egg,margarine,ghee",
        BAKERY to "bread,baguette,roll,bun,pitta,naan,tortilla,wrap,croissant,brioche,sourdough,crumpet,muffin",
        TINS to "tinned,canned,jarred,chopped tomato,passata,puree,coconut milk,stock,soy sauce,fish sauce,vinegar,mustard,ketchup,mayonnaise,worcestershire,tahini,pesto,olive,caper,gherkin,jam,honey,peanut butter,curry paste,harissa,sriracha,baked bean,chickpea,lentil,kidney bean,black bean,butter bean",
        CUPBOARD to "olive oil,vegetable oil,rapeseed oil,sunflower oil,sesame oil,flour,sugar,rice,pasta,spaghetti,noodle,couscous,quinoa,bulgur,oat,cornflour,baking powder,bicarbonate,yeast,breadcrumb,chocolate,cocoa,raisin,date,almond,walnut,cashew,pine nut,sesame,seed,oil,polenta,gelatine,vanilla,syrup,semolina",
        HERBS to "salt,pepper,paprika,cumin,coriander seed,turmeric,cinnamon,nutmeg,clove,cardamom,curry powder,chilli powder,chilli flake,oregano,thyme,rosemary,bay leaf,sage,dill,tarragon,parsley,basil,mint,chive,star anise,fennel seed,mustard seed,saffron,herb,spice,stock cube",
        FROZEN to "frozen,ice cream,puff pastry,shortcrust",
        DRINKS to "wine,beer,cider,juice,water,tea,coffee,sherry,vermouth,brandy,rum,whisky,stout"
    )

    private val index: List<Pair<String, String>> = keywords.flatMap { (aisle, list) ->
        list.split(",").map { it.trim() to aisle }
    }.sortedByDescending { it.first.length }

    /**
     * What form something comes in beats what it is made of: "frozen peas" is a
     * Frozen item, not a vegetable. Only these words get that precedence --
     * letting every Tins keyword win was filing olive oil next to the olives.
     */
    private val formMarkers = listOf(
        "frozen" to FROZEN,
        "tinned" to TINS,
        "canned" to TINS,
        "jarred" to TINS
    )

    fun classify(ingredientName: String): String {
        val key = IngredientParser.normaliseName(ingredientName)
        formMarkers.firstOrNull { TextMatch.containsWord(key, it.first) }?.let { return it.second }
        return index.firstOrNull { (word, _) -> TextMatch.containsWord(key, word) }?.second ?: OTHER
    }

    /** Position in the walking order; unknown aisles sort last. */
    fun walkingOrder(aisle: String): Int =
        order.indexOf(aisle).takeIf { it >= 0 } ?: order.size
}
