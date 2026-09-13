# Pantry

An Android recipe catalogue that imports from the web, works out macros, plans a
week of meals, and turns any of it into a single shopping list -- optionally
loaded straight into a Tesco basket.

Kotlin, Jetpack Compose, Room. No API keys, no accounts, no backend.

## Opening it

1. Android Studio -> Open -> this folder.
2. The Gradle wrapper JAR is not checked in. Android Studio will offer to
   generate it on first sync; if you would rather do it yourself, run
   `gradle wrapper --gradle-version 8.11.1` once with a local Gradle install.
3. Sync, then Run. `minSdk 26`, `compileSdk 35`, JDK 17.

`./gradlew test` runs the unit tests (parser, shopping aggregation, durations,
seasonality). They are plain JVM tests and need no device.

## How each feature is built

| # | Feature | Where |
|---|---------|-------|
| 1 | Recipe catalogue | `data/db` (Room), `repo/RecipeRepository`, `ui/recipes` |
| 2 | Import from a link | `importer/RecipeUrlImporter`, `ui/importer` |
| 3 | Recipe to shopping checklist | `shopping/ShoppingListBuilder`, `ui/shopping` |
| 4 | Many recipes into one list | same builder; long-press recipes to multi-select, or "Shop for week" |
| 5 | Macronutrients | `nutrition/`, `domain/Macros.kt` |
| 6 | Weekly meal plan | `repo/MealPlanRepository`, `ui/plan` |
| 7 | Seasonality | `seasonality/Seasonality.kt` |
| 8 | Cooking time | `RecipeEntity.displayMinutes`, `importer/Iso8601Duration`, shown on every card |
| 9 | Fill a Tesco basket | `tesco/TescoBasketActivity`, `assets/tesco_consent.js` |
| 10 | Suggest recipes from Tesco offers | not built -- see Backlog |

### 2. Importing

Fetch the page with OkHttp, then try three strategies in order:

1. **JSON-LD** `schema.org/Recipe` -- what almost every recipe site publishes so
   Google can show rich results. Handles `@graph` wrappers, `HowToSection`
   instructions, ISO-8601 durations, and the half-dozen shapes `image` comes in.
2. **Microdata** (`itemprop=`) for older sites.
3. **Layout heuristics** -- picks the list whose items look most like ingredient
   lines. Results from this path are flagged in the UI as guessed.

Every import lands in an editable preview before it is saved, so a bad parse is a
30-second fix rather than a dead end. The app also registers for `ACTION_SEND`,
so you can share a page to Pantry from your browser instead of copying links.

### 3, 4. Shopping lists

Ingredient lines are parsed into `Quantity` values (`domain/IngredientParser`),
which handle mixed fractions (`1 1/2`), unicode fractions (`½`), ranges (`2-3`,
rounded up), trailing count units (`2 garlic cloves`), and parenthetical asides.

Merging is deliberately conservative: quantities combine only when physically
comparable. 1 kg + 500 g of potatoes becomes `1.5 kg`, but 400 g of tomatoes and
2 tbsp of tomato puree stay as `400 g + 2 tbsp` rather than being forced into one
wrong number. Lines with no quantity become "as needed" instead of an invented
amount. The list is grouped into supermarket aisles in walking order.

Servings scale: shopping for 6 from a 4-serving recipe multiplies quantities by
1.5, and the meal plan carries per-meal serving counts into the list.

### 5. Macronutrients

Per-100g figures come from two sources, in this order:

1. **`assets/staples.csv`** -- roughly 170 bundled entries for raw produce, meat,
   dairy and store-cupboard basics. This is first because Open Food Facts is a
   database of *packaged products*: it is excellent for a branded jar of pesto and
   poor for "2 carrots".
2. **Open Food Facts** search, for everything else. No key, no sign-up. Results
   are scored for name overlap and plausibility rather than taking the first hit.

Both are cached in Room, including misses, so the network is hit once per new
ingredient.

Converting a recipe quantity to grams needs a density (for volumes) or a typical
item weight (for counts); both tables live in `domain/Macros.kt`. Where a default
had to be used, the per-ingredient breakdown says "estimated weight", and the
recipe screen shows what share of ingredients actually contributed numbers.
Ingredients with no match are left out rather than guessed at.

### 6, 7. Planning and seasonality

The week grid holds breakfast/lunch/dinner per day with per-meal servings, and
shows kcal and total cooking time per day. "Plan my week" fills empty dinners,
scoring recipes by in-season ingredients, favourites, and cooking time -- quick
on weeknights, longer at the weekend -- while avoiding the same recipe twice
running.

`Seasonality.kt` holds a UK growing calendar. Ingredients it does not know about
(store cupboard, meat, imports) report `UNKNOWN` and are simply not flagged,
rather than guessed. Out-of-season items get substitution suggestions.

### 9. Tesco basket

`TescoBasketActivity` opens Tesco's groceries site in a WebView and walks the
unchecked shopping list one item at a time. Each item puts its own search page in
front of you -- `.../search?query=chopped+tomatoes` -- and **you** tap Add on
Tesco's page, using their quantity stepper if you want two of something. Tapping
`Added` in the bar below only advances the queue; it does not add anything.

The only thing this depends on is the shape of that search URL, which is a link
you could bookmark. There is no DOM automation, so a Tesco redesign cannot
quietly break it. `assets/tesco_consent.js` is all that gets injected, and it
only dismisses the cookie banner.

Per item the bar shows the name, the aggregated quantity (`600 g`, so you know to
take two tins) and progress. Tapping the name lets you retype the search without
losing your place, for lines the parser rendered awkwardly. `Added` is disabled
while the next page loads, so an impatient double tap cannot skip an item. A
chevron steps back and clears that item's decision, and the counter in the app
bar opens the full queue to jump anywhere.

Honest limits:

- The app cannot see inside your basket. "Added" means you said so; Tesco's own
  basket counter, on screen throughout, is the real check. The summary sheet says
  this rather than claiming a clean run.
- It never touches checkout. **Check the basket before you pay.**
- Automating a retailer's site is arguably against their terms of service. This
  drives the ordinary UI at human speed, as you, on your own account.

At the end the summary offers to tick the items you marked as added off the
shopping list. It is offered, never done silently, and the run reports the added
names back through `EXTRA_ADDED` for `ShoppingScreen` to apply.

## Backlog

**10. Recipe suggestions from Tesco offers.** Not built. The shape it would take:
read the Clubcard Prices / offers listing in the same WebView session, normalise
product names through `IngredientParser.normaliseName`, and score catalogue
recipes by how many of their ingredients are currently discounted -- reusing the
scoring hook already in `MealPlanRepository.score`. Note this one *would* need
page scraping, which feature 9 deliberately no longer does, so it carries a
fragility the rest of the app has been kept clear of.

There is a mockup of the basket flow in `app/design/pantry-tesco-flow.svg` (open it
in Android Studio, or any SVG viewer); `app/design/build_flow_svg.py` regenerates it
from the palette in `ui/theme/Theme.kt`.

Other things worth doing next: editing a saved recipe in place (import can update
by URL, but there is no editor), a manual per-ingredient nutrition override
(`NutritionRepository.setManualFacts` exists but has no UI), and Room migrations
in place of `fallbackToDestructiveMigration`.
