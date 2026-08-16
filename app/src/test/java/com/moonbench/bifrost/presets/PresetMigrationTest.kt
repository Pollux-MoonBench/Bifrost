package com.moonbench.bifrost.presets

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetMigrationTest {

    private fun typeAt(json: String, index: Int): String =
        JSONArray(json).getJSONObject(index).getString("animationType")

    @Test fun ambilightIsRewrittenToAmbient() {
        val stored = """[{"name":"Default","animationType":"AMBILIGHT","color":-1}]"""

        val migrated = PresetMigration.migrateAnimationNames(stored)!!

        assertEquals("AMBIENT", typeAt(migrated, 0))
    }

    @Test fun otherFieldsSurvive() {
        val stored = """[{"name":"Default","animationType":"AMBILIGHT","color":-65536,"brightness":120}]"""

        val migrated = PresetMigration.migrateAnimationNames(stored)!!
        val obj = JSONArray(migrated).getJSONObject(0)

        assertEquals("Default", obj.getString("name"))
        assertEquals(-65536, obj.getInt("color"))
        assertEquals(120, obj.getInt("brightness"))
    }

    @Test fun currentNamesAreLeftUntouched() {
        val stored = """[{"name":"A","animationType":"AMBIENT"},{"name":"B","animationType":"PIPBOY"}]"""

        assertEquals(stored, PresetMigration.migrateAnimationNames(stored))
    }

    @Test fun unknownNamesAreLeftForTheParsersToHandle() {
        val stored = """[{"name":"A","animationType":"NOPE"}]"""

        assertEquals(stored, PresetMigration.migrateAnimationNames(stored))
    }

    @Test fun malformedStorageIsReportedRatherThanRewritten() {
        assertNull(PresetMigration.migrateAnimationNames("not json"))
    }
}
