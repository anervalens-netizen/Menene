package ro.mehene.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafDocumentPathTest {
    @Test
    fun findsTheParentDocumentId() {
        assertEquals(
            "486F-ACE7:Menene/Serial/Season 01",
            parentDocumentId("486F-ACE7:Menene/Serial/Season 01/Episode 01.mp4"),
        )
    }

    @Test
    fun rootDocumentHasNoParentInsideTheTree() {
        assertNull(parentDocumentId("486F-ACE7:Menene"))
    }

    @Test
    fun acceptsOnlyTheRootAndItsDescendants() {
        val root = "486F-ACE7:Menene"

        assertTrue(isDocumentWithinRoot(root, root))
        assertTrue(
            isDocumentWithinRoot(
                root,
                "486F-ACE7:Menene/Serial/Season 01/Episode 01.mp4",
            ),
        )
        assertFalse(isDocumentWithinRoot(root, "486F-ACE7:Menene-Alt/Episode 01.mp4"))
        assertFalse(isDocumentWithinRoot(root, "486F-ACE7:Alt/Episode 01.mp4"))
    }
}
