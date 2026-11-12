package com.octorice.opengifwidget.shape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import com.octorice.opengifwidget.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A "cookie cutter" for the widget's picture.
 * [aspect] = width / height of the shape's box (1 = square). [drawUnit] draws the shape into a
 * path inside a 1 x 1 square (0..1 on both axes); it is stretched to the box when used.
 * [id] is what gets saved, so never change it once released.
 *
 * Most shapes look the same on every widget. A pattern (see [DotsPattern]) changes with the
 * widget's size: [forSize] gives the shape as drawn on a widget of that size (for most shapes,
 * itself), and [base] the shape you picked (what's saved as the widget's shape).
 */
open class WidgetShape(
    val id: String,
    @param:StringRes val nameRes: Int,
    val aspect: Float,
    private val drawUnit: Path.() -> Unit
) {
    /** The shape's outline, sized to [width] x [height] pixels. */
    fun path(width: Float, height: Float): Path {
        val path = Path().apply(drawUnit)
        path.transform(Matrix().apply { setScale(width, height) })
        return path
    }

    /** This shape as drawn on a widget of [widthDp] x [heightDp]. */
    open fun forSize(widthDp: Float, heightDp: Float): WidgetShape = this

    /** The shape you pick in the editor (for a pattern's sized version: the pattern itself). */
    open val base: WidgetShape get() = this

    /** A pattern's sized version from the part of its id after "@" (e.g. "6x3"), or null. */
    open fun sized(key: String): WidgetShape? = null
}

/**
 * An arch: straight sides and bottom, a round top. It takes the widget's whole shape (a tall
 * widget gets a tall arch); the top is a half circle as wide as the widget, or, on widgets too wide
 * for that, corners as round as the height allows. Sized ids look like "arch@1.50" (the widget's
 * width / height, in steps of 0.05, so small size changes don't re-cut the frames).
 */
class ArchPattern(id: String, @StringRes nameRes: Int) :
    WidgetShape(id, nameRes, aspect = ICON_ASPECT, drawUnit = { arch(ICON_ASPECT) }) {

    private val sized = HashMap<Int, WidgetShape>()

    override fun forSize(widthDp: Float, heightDp: Float): WidgetShape =
        withAspect((widthDp / heightDp * 20).roundToInt())

    override fun sized(key: String): WidgetShape? =
        key.toFloatOrNull()?.let { withAspect((it * 20).roundToInt()) }

    /** One shared copy per aspect step ([twentieths] = aspect x 20). */
    private fun withAspect(twentieths: Int): WidgetShape = synchronized(sized) {
        val steps = twentieths.coerceIn(6, 80) // 0.3 .. 4
        sized.getOrPut(steps) {
            val aspect = steps / 20f
            Sized(this, String.format(java.util.Locale.ROOT, "%s@%.2f", id, aspect), aspect)
        }
    }

    private class Sized(val parent: ArchPattern, id: String, aspect: Float) :
        WidgetShape(id, parent.nameRes, aspect, drawUnit = { arch(aspect) }) {
        override val base: WidgetShape get() = parent
    }

    companion object {
        /** The picker icon's (and a not-yet-sized widget's) arch: a bit taller than wide. */
        private const val ICON_ASPECT = 0.85f

        /** An arch [aspect] wide and 1 tall, squeezed into the 1 x 1 square (the box stretches it back). */
        private fun Path.arch(aspect: Float) {
            val radius = min(aspect / 2, 1f)
            val radii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f) // round top corners only
            val real = Path().apply { addRoundRect(android.graphics.RectF(0f, 0f, aspect, 1f), radii, Path.Direction.CW) }
            real.transform(Matrix().apply { setScale(1f / aspect, 1f) })
            addPath(real)
        }
    }
}

/**
 * A grid of round dots that overlap a little ("stuck together"). The dots keep about the same
 * size on screen ([cellDp]); a bigger widget gets more of them instead of bigger ones.
 * Its sized versions have ids like "dots@6x3", so the frames remember which grid they were cut with.
 * Picked on its own (the editor's icon, or a widget whose size isn't known yet), it's 3 x 3.
 */
class DotsPattern(
    id: String,
    @StringRes nameRes: Int,
    private val cellDp: Float,
    private val overlap: Float
) : WidgetShape(id, nameRes, aspect = 1f, drawUnit = { dots(3, 3, overlap) }) {

    private val sized = HashMap<Pair<Int, Int>, WidgetShape>()

    override fun forSize(widthDp: Float, heightDp: Float): WidgetShape =
        grid(
            columns = (widthDp / cellDp).roundToInt().coerceIn(1, MAX_DOTS),
            rows = (heightDp / cellDp).roundToInt().coerceIn(1, MAX_DOTS)
        )

    override fun sized(key: String): WidgetShape? {
        val (columns, rows) = key.split('x').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return null
        return grid(columns.coerceIn(1, MAX_DOTS), rows.coerceIn(1, MAX_DOTS))
    }

    /** The pattern with this many dots (one shared copy per grid, so previews can reuse their masks). */
    fun grid(columns: Int, rows: Int): WidgetShape = synchronized(sized) {
        sized.getOrPut(columns to rows) { Sized(this, columns, rows, overlap) }
    }

    private class Sized(val parent: DotsPattern, columns: Int, rows: Int, overlap: Float) : WidgetShape(
        "${parent.id}@${columns}x$rows",
        parent.nameRes,
        // The grid's own proportions, so the dots stay round.
        aspect = (columns + 2 * overlap) / (rows + 2 * overlap),
        drawUnit = { dots(columns, rows, overlap) }
    ) {
        override val base: WidgetShape get() = parent
    }

    companion object {
        private const val MAX_DOTS = 12

        /**
         * [columns] x [rows] dots, one grid step apart, each a bit bigger than half a step so
         * neighbours overlap. Stretched over the 1 x 1 square (the box has the grid's aspect, so
         * they come out round). All drawn the same way round, so overlaps simply join.
         */
        private fun Path.dots(columns: Int, rows: Int, overlap: Float) {
            val radius = 0.5f + overlap
            val width = columns + 2 * overlap
            val height = rows + 2 * overlap
            for (column in 0 until columns) {
                for (row in 0 until rows) {
                    val cx = (column + 0.5f + overlap) / width
                    val cy = (row + 0.5f + overlap) / height
                    addOval(
                        cx - radius / width, cy - radius / height, cx + radius / width, cy + radius / height,
                        Path.Direction.CW
                    )
                }
            }
        }
    }
}

/** Every shape the app knows. To add one, add an entry here (and its name in strings.xml). */
object Shapes {

    val CIRCLE = WidgetShape("circle", R.string.shape_circle, aspect = 1f) {
        addOval(0f, 0f, 1f, 1f, Path.Direction.CW)
    }

    val HEART = WidgetShape("heart", R.string.shape_heart, aspect = 1f) {
        // A classic heart: two round lobes on top, a point at the bottom, filling most of the square.
        moveTo(0.5f, 0.95f)
        cubicTo(0.30f, 0.80f, 0.02f, 0.62f, 0.02f, 0.34f)
        cubicTo(0.02f, 0.15f, 0.16f, 0.05f, 0.29f, 0.05f)
        cubicTo(0.39f, 0.05f, 0.47f, 0.11f, 0.5f, 0.21f)
        cubicTo(0.53f, 0.11f, 0.61f, 0.05f, 0.71f, 0.05f)
        cubicTo(0.84f, 0.05f, 0.98f, 0.15f, 0.98f, 0.34f)
        cubicTo(0.98f, 0.62f, 0.70f, 0.80f, 0.5f, 0.95f)
        close()
    }

    /** A classic 5-point star: rounded tips, crisp (barely softened) dips between the arms. */
    val STAR = WidgetShape("star", R.string.shape_star, aspect = 1f) {
        roundedStar(inner = 0.52f, tipRound = 0.36f, valleyRound = 0.12f)
    }

    /** A fat, squishy star: short wide arms, very round tips, crisp dips. */
    val CHONKY_STAR = WidgetShape("chonky_star", R.string.shape_chonky_star, aspect = 1f) {
        roundedStar(inner = 0.60f, tipRound = 0.47f, valleyRound = 0.10f)
    }

    /**
     * A big slanted 4-point sparkle with a small one at its top right. Wider than tall
     * (its box keeps the drawing's own proportions), so its aspect comes from the drawing.
     */
    val SPARKLE = WidgetShape("sparkle", R.string.shape_sparkle, aspect = Sparkle.aspect) {
        Sparkle.draw(this)
    }

    /** A round badge with a scalloped edge: 12 round bumps with small notches between them. */
    val SCALLOP = WidgetShape("scallop", R.string.shape_scallop, aspect = 1f) {
        scallop(bumps = 12, depth = 0.18f) // "3. 12 bumps, deep", chosen by the user
    }

    /** Four round leaves (overlapping circles) with small notches between them. */
    val CLOVER = WidgetShape("clover", R.string.shape_clover, aspect = 1f) {
        val spread = 0.25f // how far each leaf sits from the middle
        val leaf = 0.27f   // leaf radius (bigger than the spread, so leaves overlap)
        circles(
            listOf(-1f, 1f).flatMap { sx -> listOf(-1f, 1f).map { sy -> floatArrayOf(sx * spread, sy * spread, leaf) } } +
                listOf(floatArrayOf(0f, 0f, spread * 1.42f)) // fills the middle
        )
    }

    /** Five round petals, one pointing up, around a filled middle. */
    val FLOWER = WidgetShape("flower", R.string.shape_flower, aspect = 1f) {
        val spread = 0.30f
        val petal = 0.24f
        circles(
            List(5) { i ->
                val angle = -Math.PI / 2 + 2 * Math.PI * i / 5
                floatArrayOf((spread * cos(angle)).toFloat(), (spread * sin(angle)).toFloat(), petal)
            } + listOf(floatArrayOf(0f, 0f, spread))
        )
    }

    /** A badge with 14 sharp points. */
    val BURST = WidgetShape("burst", R.string.shape_burst, aspect = 1f) {
        val points = 14
        val inner = 0.76f // how far in the dips between points are
        val corners = List(2 * points) { i ->
            val radius = if (i % 2 == 0) 1.0 else inner.toDouble()
            val angle = -Math.PI / 2 + i * Math.PI / points
            floatArrayOf((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
        }
        val fit = Fit(corners.map { floatArrayOf(it[0], it[1], 0f) })
        corners.forEachIndexed { i, c -> if (i == 0) moveTo(fit.x(c[0]), fit.y(c[1])) else lineTo(fit.x(c[0]), fit.y(c[1])) }
        close()
    }

    /**
     * A grid of round dots stuck together ("2. Stuck together", chosen by the user). The dots stay
     * about 56 dp wide: a 2x2 widget gets about 3 x 3, a 4x2 about 6 x 3.
     */
    val DOTS = DotsPattern("dots", R.string.shape_dots, cellDp = 56f, overlap = 0.06f)

    /**
     * A crescent moon ("3. Fatter", chosen by the user): a circle with a smaller circle cut out of
     * its upper left; each tip is rounded off by a tiny circle that touches both edges.
     */
    val MOON = WidgetShape("moon", R.string.shape_moon, aspect = 1f) {
        val outline = moonOutline(offset = 0.72, cut = 0.86, tip = 0.06, direction = Math.toRadians(233.0))
        val fit = Fit(outline.map { floatArrayOf(it[0], it[1], 0f) })
        outline.forEachIndexed { i, p -> if (i == 0) moveTo(fit.x(p[0]), fit.y(p[1])) else lineTo(fit.x(p[0]), fit.y(p[1])) }
        close()
    }

    /**
     * The moon's outline as many points: the big circle (radius 1, at 0,0) minus a circle of radius
     * [cut] whose center is [offset] away in [direction]. The two sharp tips are replaced by arcs
     * of tiny circles (radius [tip]) that sit inside the moon touching both edges.
     */
    private fun moonOutline(offset: Double, cut: Double, tip: Double, direction: Double): List<FloatArray> {
        val cx = offset * cos(direction)
        val cy = offset * sin(direction)
        val ux = cx / offset
        val uy = cy / offset
        // Tip circle centers: (1 - tip) from the big circle's center and (cut + tip) from the cut's.
        val a = 1 - tip
        val b = cut + tip
        val along = (a * a - b * b + offset * offset) / (2 * offset)
        val side = kotlin.math.sqrt(max(0.0, a * a - along * along))
        val tip1 = doubleArrayOf(along * ux - side * uy, along * uy + side * ux)
        val tip2 = doubleArrayOf(along * ux + side * uy, along * uy - side * ux)

        val points = mutableListOf<FloatArray>()
        fun arc(x: Double, y: Double, radius: Double, from: Double, to: Double, steps: Int) {
            for (k in 0..steps) {
                val angle = from + (to - from) * k / steps
                points += floatArrayOf((x + radius * cos(angle)).toFloat(), (y + radius * sin(angle)).toFloat())
            }
        }
        fun angleOf(fromX: Double, fromY: Double, toX: Double, toY: Double) = kotlin.math.atan2(toY - fromY, toX - fromX)
        // The shorter way from one angle to another.
        fun nearest(from: Double, to: Double): Double {
            var result = to
            while (result - from > Math.PI) result -= 2 * Math.PI
            while (from - result > Math.PI) result += 2 * Math.PI
            return result
        }

        // 1. The big outer edge from tip 1 to tip 2, the long way round (the side away from the cut).
        val outerFrom = angleOf(0.0, 0.0, tip1[0], tip1[1])
        var outerTo = angleOf(0.0, 0.0, tip2[0], tip2[1])
        while (outerTo > outerFrom) outerTo -= 2 * Math.PI
        val middle = (outerFrom + outerTo) / 2
        if (cos(middle) * ux + sin(middle) * uy > 0) outerTo += 2 * Math.PI
        arc(0.0, 0.0, 1.0, outerFrom, outerTo, 240)
        // 2. Round tip 2: from where it touches the big circle to where it touches the cut.
        fun touchOuter(t: DoubleArray) = doubleArrayOf(t[0] / a, t[1] / a)
        fun touchCut(t: DoubleArray) = doubleArrayOf(cx + (t[0] - cx) * cut / b, cy + (t[1] - cy) * cut / b)
        run {
            val from = angleOf(tip2[0], tip2[1], touchOuter(tip2)[0], touchOuter(tip2)[1])
            val to = nearest(from, angleOf(tip2[0], tip2[1], touchCut(tip2)[0], touchCut(tip2)[1]))
            arc(tip2[0], tip2[1], tip, from, to, 24)
        }
        // 3. The inner (cut) edge back to tip 1, the short way.
        run {
            val from = angleOf(cx, cy, tip2[0], tip2[1])
            val to = nearest(from, angleOf(cx, cy, tip1[0], tip1[1]))
            arc(cx, cy, cut, from, to, 240)
        }
        // 4. Round tip 1: from the cut back to the big circle.
        run {
            val from = angleOf(tip1[0], tip1[1], touchCut(tip1)[0], touchCut(tip1)[1])
            val to = nearest(from, angleOf(tip1[0], tip1[1], touchOuter(tip1)[0], touchOuter(tip1)[1]))
            arc(tip1[0], tip1[1], tip, from, to, 24)
        }
        return points
    }

    /** An arch that takes the widget's shape (see ArchPattern). */
    val ARCH = ArchPattern("arch", R.string.shape_arch)

    /** In the order the editor shows them (after "None"). */
    val all: List<WidgetShape> =
        listOf(CIRCLE, HEART, STAR, CHONKY_STAR, SPARKLE, SCALLOP, CLOVER, FLOWER, BURST, DOTS, ARCH, MOON)

    /**
     * Fits a drawing into the 1 x 1 square, centered, proportions kept. [items] are circles
     * (x, y, radius) or points (radius 0), in any units.
     */
    private class Fit(items: List<FloatArray>) {
        private val minX = items.minOf { it[0] - it[2] }
        private val minY = items.minOf { it[1] - it[2] }
        private val width = items.maxOf { it[0] + it[2] } - minX
        private val height = items.maxOf { it[1] + it[2] } - minY
        private val size = max(width, height)
        fun x(v: Float) = (v - minX + (size - width) / 2) / size
        fun y(v: Float) = (v - minY + (size - height) / 2) / size
        fun r(v: Float) = v / size
    }

    /**
     * Overlapping circles (x, y, radius) as one shape: all drawn the same way round, so where they
     * overlap they simply join (no holes).
     */
    private fun Path.circles(items: List<FloatArray>) {
        val fit = Fit(items)
        for (c in items) addCircle(fit.x(c[0]), fit.y(c[1]), fit.r(c[2]), Path.Direction.CW)
    }

    /**
     * A circle whose edge is a ring of [bumps] round bumps, filling the 1 x 1 square. Going around,
     * the distance from the center rises like a half circle from a notch ([depth] in from the edge)
     * to a bump top (the edge) and back. Drawn as many short lines: smooth after anti-aliasing.
     */
    private fun Path.scallop(bumps: Int, depth: Float, steps: Int = 1440) {
        val base = 1f - depth
        for (k in 0 until steps) {
            val angle = 2 * Math.PI * k / steps
            val t = ((angle * bumps / (2 * Math.PI)) % 1.0) * 2 - 1 // -1..1 across one bump
            val radius = base + depth * kotlin.math.sqrt(max(0.0, 1 - t * t))
            val turned = angle - Math.PI / 2 + Math.PI / bumps // a bump top points straight up
            val x = (0.5 + 0.5 * radius * cos(turned)).toFloat()
            val y = (0.5 + 0.5 * radius * sin(turned)).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /**
     * The sparkle drawing. Each sparkle = 4 tips joined by curves that bend in toward its center
     * (so the tips stay sharp). [FAT] pushes the curves back out: 0 = thin like a drawn sparkle,
     * bigger = a fatter middle that shows more of the picture.
     */
    private object Sparkle {
        private const val PULL = 0.95f // how close the curves' handles reach to the center
        private const val FAT = 0.32f  // chosen with the user: "3. Fatter"

        // Laid out on a 0..1000 grid: center, then tips (left, top, right, bottom), slanted.
        private val big = floatArrayOf(520f, 540f) to listOf(
            floatArrayOf(90f, 540f), floatArrayOf(755f, 178f), floatArrayOf(950f, 540f), floatArrayOf(285f, 903f)
        )
        private val small = floatArrayOf(875f, 330f) to listOf(
            floatArrayOf(740f, 330f), floatArrayOf(950f, 216f), floatArrayOf(1010f, 330f), floatArrayOf(802f, 444f)
        )

        private fun lerp(a: FloatArray, b: FloatArray, t: Float) =
            floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

        /** Each side as a cubic curve: start, handle 1, handle 2, end. */
        private fun sides(sparkle: Pair<FloatArray, List<FloatArray>>): List<List<FloatArray>> {
            val (center, tips) = sparkle
            return tips.indices.map { i ->
                val a = tips[i]
                val b = tips[(i + 1) % tips.size]
                val middle = lerp(a, b, 0.5f)
                listOf(a, lerp(lerp(a, center, PULL), middle, FAT), lerp(lerp(b, center, PULL), middle, FAT), b)
            }
        }

        private val shapes = listOf(sides(big), sides(small))

        // The drawing's real edges (curves don't reach their handles), measured along the curves.
        private val bounds: FloatArray = run {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (side in shapes.flatten()) {
                for (step in 0..40) {
                    val s = step / 40f
                    val u = 1 - s
                    val x = u * u * u * side[0][0] + 3 * u * u * s * side[1][0] + 3 * u * s * s * side[2][0] + s * s * s * side[3][0]
                    val y = u * u * u * side[0][1] + 3 * u * u * s * side[1][1] + 3 * u * s * s * side[2][1] + s * s * s * side[3][1]
                    minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                }
            }
            floatArrayOf(minX, minY, maxX, maxY)
        }

        val aspect: Float = (bounds[2] - bounds[0]) / (bounds[3] - bounds[1])

        /** Draws both sparkles, stretched to fill the 1 x 1 square (the box then has [aspect]). */
        fun draw(path: Path) {
            fun x(v: Float) = (v - bounds[0]) / (bounds[2] - bounds[0])
            fun y(v: Float) = (v - bounds[1]) / (bounds[3] - bounds[1])
            for (sparkle in shapes) {
                sparkle.forEachIndexed { i, side ->
                    if (i == 0) path.moveTo(x(side[0][0]), y(side[0][1]))
                    path.cubicTo(x(side[1][0]), y(side[1][1]), x(side[2][0]), y(side[2][1]), x(side[3][0]), y(side[3][1]))
                }
                path.close()
            }
        }
    }

    /**
     * A star pointing up, filling the 1 x 1 square (centered, proportions kept).
     * [inner] = how far in the dips are (0..1, bigger = fatter arms). Every corner is rounded with a
     * curve that starts [tipRound] (tips) or [valleyRound] (dips) of the way along its two edges.
     */
    private fun Path.roundedStar(inner: Float, tipRound: Float, valleyRound: Float, points: Int = 5) {
        // The star's corners: tips and dips taking turns, starting with the top tip.
        val corners = List(2 * points) { i ->
            val radius = if (i % 2 == 0) 1.0 else inner.toDouble()
            val angle = -Math.PI / 2 + i * Math.PI / points
            floatArrayOf((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
        }
        // Each corner becomes a curve: from a point on the edge before it to a point on the edge
        // after it, pulled toward the corner. Straight lines join the curves.
        val curves = corners.indices.map { i ->
            val corner = corners[i]
            val before = corners[(i - 1 + corners.size) % corners.size]
            val after = corners[(i + 1) % corners.size]
            val t = if (i % 2 == 0) tipRound else valleyRound
            Triple(
                floatArrayOf(corner[0] + (before[0] - corner[0]) * t, corner[1] + (before[1] - corner[1]) * t),
                corner,
                floatArrayOf(corner[0] + (after[0] - corner[0]) * t, corner[1] + (after[1] - corner[1]) * t)
            )
        }
        // Its real size (the curves don't reach the corners), to stretch it evenly over the square.
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for ((start, control, end) in curves) {
            for (step in 0..20) {
                val s = step / 20f
                val x = (1 - s) * (1 - s) * start[0] + 2 * (1 - s) * s * control[0] + s * s * end[0]
                val y = (1 - s) * (1 - s) * start[1] + 2 * (1 - s) * s * control[1] + s * s * end[1]
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            }
        }
        val size = max(maxX - minX, maxY - minY)
        val offsetX = (size - (maxX - minX)) / 2 - minX
        val offsetY = (size - (maxY - minY)) / 2 - minY
        fun x(v: Float) = (v + offsetX) / size
        fun y(v: Float) = (v + offsetY) / size

        curves.forEachIndexed { i, (start, control, end) ->
            if (i == 0) moveTo(x(start[0]), y(start[1])) else lineTo(x(start[0]), y(start[1]))
            quadTo(x(control[0]), y(control[1]), x(end[0]), y(end[1]))
        }
        close()
    }

    /**
     * The shape saved under [id], or null for "None" (or a shape this version doesn't know).
     * Also reads a pattern's sized id, like "dots@6x3".
     */
    fun byId(id: String?): WidgetShape? {
        if (id == null) return null
        val shape = all.firstOrNull { it.id == id.substringBefore('@') } ?: return null
        if ('@' !in id) return shape
        return shape.sized(id.substringAfter('@')) ?: shape
    }
}

/**
 * Masks: a picture of the shape, white where the picture shows and see-through where it's cut.
 * Partly see-through pixels (soft edges, and later gray areas) let the picture partly show.
 * The same mask cuts the widget frames, colors the widget's background and draws the editor
 * preview, so they always match.
 */
object ShapeMask {

    fun render(shape: WidgetShape, width: Int, height: Int): Bitmap {
        val mask = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE } // anti-aliased = soft edges
        Canvas(mask).drawPath(shape.path(mask.width.toFloat(), mask.height.toFloat()), paint)
        return mask
    }
}
