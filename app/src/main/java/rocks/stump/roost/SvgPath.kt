package rocks.stump.roost

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Parses an SVG path `d` string into an [android.graphics.Path]. Supports the full command set
 * (M/L/H/V/C/S/Q/T/A/Z, absolute + relative); elliptical arcs are converted to cubic Béziers.
 * Framework-only per ADR-0003 — no SVG library.
 *
 * Governing: ADR-0003 (icon rendering strategy), Gitea issue #3.
 */
object SvgPath {

    fun parse(d: String): Path {
        val path = Path()
        val lex = Lexer(d)
        var cx = 0.0; var cy = 0.0            // current point
        var sx = 0.0; var sy = 0.0            // subpath start
        var pcx = 0.0; var pcy = 0.0          // previous control point (for S/T)
        var prev = ' '

        while (true) {
            val raw = lex.command() ?: break
            val rel = raw.isLowerCase()
            val cmd = raw.uppercaseChar()
            var eff = cmd
            do {
                when (eff) {
                    'M' -> {
                        var x = lex.num(); var y = lex.num()
                        if (rel) { x += cx; y += cy }
                        cx = x; cy = y; sx = x; sy = y
                        path.moveTo(x.toFloat(), y.toFloat())
                        eff = 'L' // subsequent implicit pairs are lineTo
                    }
                    'L' -> {
                        var x = lex.num(); var y = lex.num()
                        if (rel) { x += cx; y += cy }
                        cx = x; cy = y; path.lineTo(x.toFloat(), y.toFloat())
                    }
                    'H' -> { var x = lex.num(); if (rel) x += cx; cx = x; path.lineTo(x.toFloat(), cy.toFloat()) }
                    'V' -> { var y = lex.num(); if (rel) y += cy; cy = y; path.lineTo(cx.toFloat(), y.toFloat()) }
                    'C' -> {
                        var x1 = lex.num(); var y1 = lex.num(); var x2 = lex.num(); var y2 = lex.num()
                        var x = lex.num(); var y = lex.num()
                        if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                        path.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
                        pcx = x2; pcy = y2; cx = x; cy = y
                    }
                    'S' -> {
                        var x2 = lex.num(); var y2 = lex.num(); var x = lex.num(); var y = lex.num()
                        if (rel) { x2 += cx; y2 += cy; x += cx; y += cy }
                        val x1 = if (prev == 'C' || prev == 'S') 2 * cx - pcx else cx
                        val y1 = if (prev == 'C' || prev == 'S') 2 * cy - pcy else cy
                        path.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
                        pcx = x2; pcy = y2; cx = x; cy = y
                    }
                    'Q' -> {
                        var x1 = lex.num(); var y1 = lex.num(); var x = lex.num(); var y = lex.num()
                        if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                        path.quadTo(x1.toFloat(), y1.toFloat(), x.toFloat(), y.toFloat())
                        pcx = x1; pcy = y1; cx = x; cy = y
                    }
                    'T' -> {
                        var x = lex.num(); var y = lex.num()
                        if (rel) { x += cx; y += cy }
                        val x1 = if (prev == 'Q' || prev == 'T') 2 * cx - pcx else cx
                        val y1 = if (prev == 'Q' || prev == 'T') 2 * cy - pcy else cy
                        path.quadTo(x1.toFloat(), y1.toFloat(), x.toFloat(), y.toFloat())
                        pcx = x1; pcy = y1; cx = x; cy = y
                    }
                    'A' -> {
                        val rx = lex.num(); val ry = lex.num(); val rot = lex.num()
                        val laf = lex.flag(); val sf = lex.flag()
                        var x = lex.num(); var y = lex.num()
                        if (rel) { x += cx; y += cy }
                        arc(path, cx, cy, rx, ry, rot, laf, sf, x, y)
                        cx = x; cy = y
                    }
                    'Z' -> { path.close(); cx = sx; cy = sy }
                }
                prev = if (eff == 'L' && cmd == 'M') 'M' else eff
            } while (eff != 'Z' && lex.moreArgs())
            prev = cmd
        }
        return path
    }

    /** SVG elliptical arc → cubic Béziers appended to [path]. */
    private fun arc(
        path: Path, x0: Double, y0: Double, rx0: Double, ry0: Double,
        rotDeg: Double, laf: Int, sf: Int, x: Double, y: Double
    ) {
        if (rx0 == 0.0 || ry0 == 0.0) { path.lineTo(x.toFloat(), y.toFloat()); return }
        var rx = abs(rx0); var ry = abs(ry0)
        val phi = Math.toRadians(rotDeg % 360.0)
        val cosP = cos(phi); val sinP = sin(phi)

        val dx = (x0 - x) / 2.0; val dy = (y0 - y) / 2.0
        val x1p = cosP * dx + sinP * dy
        val y1p = -sinP * dx + cosP * dy

        var rxs = rx * rx; var rys = ry * ry
        val lambda = x1p * x1p / rxs + y1p * y1p / rys
        if (lambda > 1.0) { val s = sqrt(lambda); rx *= s; ry *= s; rxs = rx * rx; rys = ry * ry }

        val sign = if (laf == sf) -1.0 else 1.0
        var numer = rxs * rys - rxs * y1p * y1p - rys * x1p * x1p
        if (numer < 0.0) numer = 0.0
        val denom = rxs * y1p * y1p + rys * x1p * x1p
        val co = if (denom == 0.0) 0.0 else sign * sqrt(numer / denom)
        val cxp = co * (rx * y1p / ry)
        val cyp = co * (-ry * x1p / rx)
        val cx = cosP * cxp - sinP * cyp + (x0 + x) / 2.0
        val cy = sinP * cxp + cosP * cyp + (y0 + y) / 2.0

        val ux = (x1p - cxp) / rx; val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx; val vy = (-y1p - cyp) / ry
        val theta1 = angle(1.0, 0.0, ux, uy)
        var dtheta = angle(ux, uy, vx, vy)
        if (sf == 0 && dtheta > 0) dtheta -= 2 * PI
        if (sf == 1 && dtheta < 0) dtheta += 2 * PI

        val segs = ceil(abs(dtheta) / (PI / 2)).toInt().coerceAtLeast(1)
        val delta = dtheta / segs
        val t = tan(delta / 4)
        val alpha = sin(delta) * (sqrt(4 + 3 * t * t) - 1) / 3
        var th = theta1
        for (i in 0 until segs) {
            val th2 = th + delta
            val p1x = rx * cos(th); val p1y = ry * sin(th)
            val p2x = rx * cos(th2); val p2y = ry * sin(th2)
            val e1x = cx + cosP * p1x - sinP * p1y; val e1y = cy + sinP * p1x + cosP * p1y
            val e2x = cx + cosP * p2x - sinP * p2y; val e2y = cy + sinP * p2x + cosP * p2y
            val d1x = -rx * sin(th); val d1y = ry * cos(th)
            val d2x = -rx * sin(th2); val d2y = ry * cos(th2)
            val c1x = e1x + alpha * (cosP * d1x - sinP * d1y)
            val c1y = e1y + alpha * (sinP * d1x + cosP * d1y)
            val c2x = e2x - alpha * (cosP * d2x - sinP * d2y)
            val c2y = e2y - alpha * (sinP * d2x + cosP * d2y)
            path.cubicTo(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), e2x.toFloat(), e2y.toFloat())
            th = th2
        }
    }

    private fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
        if (len == 0.0) return 0.0
        var a = acos(((ux * vx + uy * vy) / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) a = -a
        return a
    }

    /** Tokenizes an SVG path `d` string into commands, numbers, and arc flags. */
    private class Lexer(private val s: String) {
        private var i = 0

        private fun skipSep() {
            while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun command(): Char? {
            skipSep()
            if (i >= s.length) return null
            val ch = s[i]
            return if (ch.isLetter()) { i++; ch } else null
        }

        /** True if another argument (number) precedes the next command letter. */
        fun moreArgs(): Boolean {
            skipSep()
            if (i >= s.length) return false
            val ch = s[i]
            return ch.isDigit() || ch == '-' || ch == '+' || ch == '.'
        }

        fun num(): Double {
            skipSep()
            val start = i
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') { i++; while (i < s.length && s[i].isDigit()) i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            return s.substring(start, i).toDoubleOrNull() ?: 0.0
        }

        /** Arc flags are single '0'/'1' chars and may be packed with no separator. */
        fun flag(): Int {
            skipSep()
            if (i < s.length && (s[i] == '0' || s[i] == '1')) { val f = s[i] - '0'; i++; return f }
            return num().toInt()
        }
    }
}
