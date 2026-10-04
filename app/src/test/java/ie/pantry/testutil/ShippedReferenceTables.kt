package ie.pantry.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ie.pantry.data.reference.AliasTable
import ie.pantry.data.reference.AndroidAssetSource
import ie.pantry.data.reference.DatasetLoader
import ie.pantry.data.reference.DatasetParser
import ie.pantry.data.reference.LoadResult
import ie.pantry.data.reference.ReferenceDataset
import ie.pantry.data.reference.SeasonalityTable
import ie.pantry.data.reference.SectionOrderTable
import ie.pantry.data.reference.StaplesTable
import kotlin.test.assertIs

/** Loads F2's shipped reference tables for tests that need the real, curated data (R2 AC4, R3;
 * CFC-1). Requires a Robolectric application context. Each call loads fresh from the real
 * assets, the same path `ShippedDatasetsTest` uses. */
object ShippedReferenceTables {

    private fun assetSource() = AndroidAssetSource(ApplicationProvider.getApplicationContext<Context>().assets)

    fun aliasTable(): AliasTable =
        assertIs<LoadResult.Ready<AliasTable>>(
            DatasetLoader.loadDataset(assetSource(), ReferenceDataset.ALIASES, DatasetParser::parseAliases),
        ).table

    fun staplesTable(): StaplesTable =
        assertIs<LoadResult.Ready<StaplesTable>>(
            DatasetLoader.loadDataset(assetSource(), ReferenceDataset.STAPLES, DatasetParser::parseStaples),
        ).table

    fun seasonalityTable(): SeasonalityTable =
        assertIs<LoadResult.Ready<SeasonalityTable>>(
            DatasetLoader.loadDataset(assetSource(), ReferenceDataset.SEASONALITY, DatasetParser::parseSeasonality),
        ).table

    fun sectionOrderTable(): SectionOrderTable =
        assertIs<LoadResult.Ready<SectionOrderTable>>(
            DatasetLoader.loadDataset(assetSource(), ReferenceDataset.SECTION_ORDER, DatasetParser::parseSectionOrder),
        ).table
}
