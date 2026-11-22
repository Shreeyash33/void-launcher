package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLayoutTest {
    private fun SwipeDirection.opposite(): SwipeDirection = when (this) {
        SwipeDirection.UP -> SwipeDirection.DOWN
        SwipeDirection.DOWN -> SwipeDirection.UP
        SwipeDirection.LEFT -> SwipeDirection.RIGHT
        SwipeDirection.RIGHT -> SwipeDirection.LEFT
    }

    @Test
    fun `default layout has exactly five pages with expected roles and titles`() {
        assertEquals(5, DefaultLayout.pages.size)
        assertEquals(
            listOf(0 to 0, 1 to 0, -1 to 0, 0 to -1, 0 to 1),
            DefaultLayout.pages.map { it.coordX to it.coordY },
        )
        assertEquals(1, DefaultLayout.pages.count { it.pageType == PageType.HOME })
        assertEquals(1, DefaultLayout.pages.count { it.pageType == PageType.APP_DRAWER })
        assertEquals(3, DefaultLayout.pages.count { it.pageType == PageType.WIDGET })

        val titles = DefaultLayout.pages.associate { (it.coordX to it.coordY) to it.title }
        assertEquals("Home", titles[0 to 0])
        assertEquals("Page 1,0", titles[1 to 0])
        assertEquals("Page -1,0", titles[-1 to 0])
        assertEquals("Page 0,-1", titles[0 to -1])
        assertEquals("Page 0,1", titles[0 to 1])
        assertEquals(0 to 0, DefaultLayout.home.coordX to DefaultLayout.home.coordY)
    }

    @Test
    fun `default layout has eight edges matching the fresh install seed`() {
        val expected = setOf(
            Triple(0 to 0, SwipeDirection.RIGHT, 1 to 0),
            Triple(0 to 0, SwipeDirection.LEFT, -1 to 0),
            Triple(0 to 0, SwipeDirection.UP, 0 to -1),
            Triple(0 to 0, SwipeDirection.DOWN, 0 to 1),
            Triple(1 to 0, SwipeDirection.LEFT, 0 to 0),
            Triple(-1 to 0, SwipeDirection.RIGHT, 0 to 0),
            Triple(0 to -1, SwipeDirection.DOWN, 0 to 0),
            Triple(0 to 1, SwipeDirection.UP, 0 to 0),
        )
        val actual = DefaultLayout.edges.map { edge ->
            Triple(
                edge.fromX to edge.fromY,
                edge.direction,
                edge.toX to edge.toY,
            )
        }.toSet()

        assertEquals(8, DefaultLayout.edges.size)
        assertEquals(expected, actual)
    }

    @Test
    fun `home has four outgoing edges and every neighbour links back`() {
        val home = DefaultLayout.home.coordX to DefaultLayout.home.coordY
        val homeOutgoing = DefaultLayout.edges.filter { (it.fromX to it.fromY) == home }

        assertEquals(4, homeOutgoing.size)
        assertEquals(
            setOf(
                SwipeDirection.RIGHT,
                SwipeDirection.LEFT,
                SwipeDirection.UP,
                SwipeDirection.DOWN,
            ),
            homeOutgoing.map { it.direction }.toSet(),
        )
        assertEquals(
            setOf(1 to 0, -1 to 0, 0 to -1, 0 to 1),
            homeOutgoing.map { it.toX to it.toY }.toSet(),
        )

        val neighbours = DefaultLayout.pages.filterNot { it.pageType == PageType.HOME }
        assertEquals(4, neighbours.size)
        neighbours.forEach { page ->
            val back = DefaultLayout.edges.single { edge ->
                (edge.fromX to edge.fromY) == (page.coordX to page.coordY) &&
                    (edge.toX to edge.toY) == home
            }
            val homeEdge = DefaultLayout.edges.single { edge ->
                (edge.fromX to edge.fromY) == home &&
                    (edge.toX to edge.toY) == (page.coordX to page.coordY)
            }
            assertEquals(
                "Neighbour ${page.coordX},${page.coordY} must point opposite the HOME edge",
                homeEdge.direction.opposite(),
                back.direction,
            )
        }
    }

    @Test
    fun `default layout seeds one page info widget on home`() {
        assertEquals(1, DefaultLayout.items.size)
        val item = DefaultLayout.items.single()

        assertEquals(BuiltInWidgetKind.PAGE_INFO, item.kind)
        assertEquals(DefaultLayout.home.coordX, item.coordX)
        assertEquals(DefaultLayout.home.coordY, item.coordY)
        assertEquals(1, item.col)
        assertEquals(1, item.row)
        assertEquals(2, item.colSpan)
        assertEquals(2, item.rowSpan)

        val span = BuiltInWidgets.defaultSpan(BuiltInWidgetKind.PAGE_INFO)
        assertEquals(span.colSpan, item.colSpan)
        assertEquals(span.rowSpan, item.rowSpan)
    }
}
