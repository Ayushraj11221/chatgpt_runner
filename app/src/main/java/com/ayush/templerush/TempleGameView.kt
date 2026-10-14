package com.ayush.templerush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A lightweight, asset-free pseudo-3D endless runner rendered directly with Android Canvas. */
class TempleGameView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rng = Random(System.nanoTime())
    private var lastFrame = 0L
    private var running = false
    private var mode = Mode.READY
    private enum class Mode { READY, PLAYING, DEAD }

    private var lane = 0 // -1, 0, 1
    private var visualLane = 0f
    private var jump = 0f
    private var jumpVelocity = 0f
    private var slideTimer = 0f
    private var distance = 0f
    private var coins = 0
    private var speed = 0.42f
    private var spawnTimer = 0f
    private var coinTimer = 0f
    private var shake = 0f
    private var best = 0
    private var playerLean = 0f
    private val obstacles = mutableListOf<Thing>()
    private val pickups = mutableListOf<Thing>()
    private val particles = mutableListOf<Particle>()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastTapAt = 0L
    private val horizon = 0.28f
    private val gold = Color.rgb(255, 203, 73)
    private val ivory = Color.rgb(255, 242, 204)
    private val mint = Color.rgb(91, 220, 156)
    private val dark = Color.rgb(8, 24, 19)

    private data class Thing(var lane: Int, var z: Float, val type: Type, var taken: Boolean = false)
    private enum class Type { ROCK, LOG, GAP, COIN }
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)

    init { isFocusable = true; setLayerType(View.LAYER_TYPE_HARDWARE, null) }

    fun pauseLoop() { running = false }
    fun resumeLoop() { if (!running) { running = true; lastFrame = 0L; postInvalidateOnAnimation() } }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); resumeLoop() }
    override fun onDetachedFromWindow() { pauseLoop(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0.016f else min(0.04f, (now - lastFrame) / 1_000_000_000f)
        lastFrame = now
        if (running) update(dt)
        drawWorld(canvas)
        if (mode != Mode.PLAYING) drawOverlay(canvas)
        if (running) postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        if (mode == Mode.PLAYING) {
            distance += speed * dt * 23f
            speed = min(1.12f, 0.42f + distance / 2800f)
            visualLane += (lane - visualLane) * min(1f, dt * 12f)
            if (jump > 0f || jumpVelocity > 0f) {
                jump += jumpVelocity * dt
                jumpVelocity -= 2.9f * dt
                if (jump <= 0f) { jump = 0f; jumpVelocity = 0f }
            }
            slideTimer = max(0f, slideTimer - dt)
            spawnTimer -= dt
            coinTimer -= dt
            if (spawnTimer <= 0f) {
                spawnObstacleRow()
                spawnTimer = max(0.62f, 1.25f - speed * 0.42f) + rng.nextFloat() * 0.48f
            }
            if (coinTimer <= 0f) {
                val l = rng.nextInt(-1, 2)
                for (i in 0..4) pickups.add(Thing(l, 0.98f - i * 0.095f, Type.COIN))
                coinTimer = 1.05f + rng.nextFloat() * 1.0f
            }
            val advance = dt * speed * 0.53f
            obstacles.forEach { it.z -= advance }
            pickups.forEach { it.z -= advance }
            checkCollisions()
            obstacles.removeAll { it.z < -0.12f }
            pickups.removeAll { it.z < -0.12f || it.taken }
        }
        particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.vy += 220f * dt; it.life -= dt }
        particles.removeAll { it.life <= 0f }
        shake = max(0f, shake - dt * 2.8f)
    }

    private fun spawnObstacleRow() {
        val laneA = rng.nextInt(-1, 2)
        val typeA = when (rng.nextInt(3)) { 0 -> Type.ROCK; 1 -> Type.LOG; else -> Type.GAP }
        obstacles.add(Thing(laneA, 1.03f, typeA))
        // Keep a second lane open so each pattern is fair and readable.
        if (rng.nextFloat() < 0.27f) {
            var laneB = rng.nextInt(-1, 2)
            while (laneB == laneA) laneB = rng.nextInt(-1, 2)
            obstacles.add(Thing(laneB, 1.20f, if (rng.nextBoolean()) Type.ROCK else Type.LOG))
        }
    }

    private fun checkCollisions() {
        for (o in obstacles) {
            if (o.z < 0.105f && o.z > 0.015f && o.lane == lane) {
                val avoided = when (o.type) {
                    Type.ROCK -> jump > 0.22f
                    Type.LOG -> slideTimer > 0f
                    Type.GAP -> jump > 0.30f
                    Type.COIN -> true
                }
                if (!avoided) { die(); return }
                if (o.type != Type.COIN) o.z = -1f
            }
        }
        for (c in pickups) {
            if (!c.taken && c.z < 0.12f && c.z > 0.005f && c.lane == lane) {
                c.taken = true; coins++
                val pt = project(c.lane.toFloat(), c.z)
                repeat(7) { particles.add(Particle(pt.first, pt.second, rng.nextFloat() * 100f - 50f, -rng.nextFloat() * 100f, 0.45f, gold)) }
            }
        }
    }

    private fun die() {
        mode = Mode.DEAD; shake = 1f
        best = max(best, distance.toInt())
        repeat(18) { particles.add(Particle(width * 0.5f, height * 0.72f, rng.nextFloat() * 260f - 130f, -rng.nextFloat() * 260f, 0.8f, if (it % 2 == 0) gold else mint)) }
    }

    private fun startGame() {
        lane = 0; visualLane = 0f; jump = 0f; jumpVelocity = 0f; slideTimer = 0f
        distance = 0f; coins = 0; speed = 0.42f; spawnTimer = 0.65f; coinTimer = 0.55f
        obstacles.clear(); pickups.clear(); particles.clear(); mode = Mode.PLAYING
    }

    private fun drawWorld(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.save()
        if (shake > 0f) canvas.translate((rng.nextFloat() - .5f) * shake * 18f, (rng.nextFloat() - .5f) * shake * 14f)
        // Sky and distant jungle haze.
        p.style = Paint.Style.FILL; p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h * .55f, intArrayOf(Color.rgb(11, 43, 35), Color.rgb(53, 100, 68), Color.rgb(193, 157, 91)), null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p); p.shader = null
        // Distant temple silhouette.
        p.color = Color.rgb(27, 57, 43)
        val templeY = h * .235f
        canvas.drawRect(w*.39f, templeY, w*.61f, h*.34f, p)
        val roof = Path().apply { moveTo(w*.35f, templeY); lineTo(w*.5f, h*.17f); lineTo(w*.65f, templeY); close() }; canvas.drawPath(roof, p)
        p.color = Color.rgb(111, 116, 70)
        for (i in 0..4) { val tx = w * (0.08f + i * .19f); canvas.drawCircle(tx, h*(.22f + (i%2)*.035f), w*.095f, p) }
        // Road trapezoid.
        val topY = h * horizon; val bottomY = h * 1.08f
        path.reset(); path.moveTo(w*.46f, topY); path.lineTo(w*.54f, topY); path.lineTo(w*.99f, bottomY); path.lineTo(w*.01f, bottomY); path.close()
        p.color = Color.rgb(102, 78, 50); canvas.drawPath(path, p)
        path.reset(); path.moveTo(w*.48f, topY); path.lineTo(w*.52f, topY); path.lineTo(w*.72f, bottomY); path.lineTo(w*.28f, bottomY); path.close(); p.color = Color.rgb(130, 99, 61); canvas.drawPath(path, p)
        // Road edges and perspective lane markers.
        p.color = Color.rgb(218, 180, 105); p.strokeWidth = max(2f, w*.006f)
        canvas.drawLine(w*.46f, topY, w*.01f, bottomY, p); canvas.drawLine(w*.54f, topY, w*.99f, bottomY, p)
        for (side in listOf(-1f, 1f)) {
            val xTop = w * (0.5f + side * .013f); val xBot = w * (0.5f + side * .25f)
            for (i in 0..7) {
                val z = ((i / 7f + (distance * .013f % 1f)) % 1f)
                val yy = topY + (bottomY - topY) * z * z
                val xx = xTop + (xBot - xTop) * z * z
                val len = w * (.004f + z*.018f)
                p.color = if (i % 2 == 0) Color.rgb(236, 201, 125) else Color.rgb(86, 68, 46)
                canvas.drawLine(xx - len, yy, xx + len, yy, p)
            }
        }
        drawJungleSides(canvas, w, h)
        // Render objects back to front.
        obstacles.sortedByDescending { it.z }.forEach { drawThing(canvas, it, w, h) }
        pickups.sortedByDescending { it.z }.forEach { if (!it.taken) drawCoin(canvas, it, w, h) }
        drawRunner(canvas, w, h)
        drawHud(canvas, w, h)
        canvas.restore()
    }

    private fun drawJungleSides(c: Canvas, w: Float, h: Float) {
        for (i in 0..8) {
            val z = ((i / 8f + distance * .008f) % 1f)
            val y = h * (.28f + z*z*.7f)
            val scale = .35f + z * 1.7f
            for (side in listOf(-1f, 1f)) {
                val x = w * (.5f + side * (.09f + z*.46f))
                p.color = Color.rgb(54, 48, 34); p.strokeWidth = w*.012f*scale
                c.drawLine(x, y, x, y - h*.055f*scale, p)
                p.color = if (i % 2 == 0) Color.rgb(35, 91, 53) else Color.rgb(48, 111, 61)
                c.drawCircle(x, y - h*.06f*scale, w*.045f*scale, p)
                c.drawCircle(x - side*w*.025f*scale, y - h*.035f*scale, w*.032f*scale, p)
            }
        }
    }

    private fun project(laneF: Float, z: Float): Pair<Float, Float> {
        val w = width.toFloat(); val h = height.toFloat()
        val depth = (1f - z).coerceIn(0f, 1.15f)
        val perspective = depth * depth
        val x = w*.5f + laneF * w*.25f * perspective
        val y = h*horizon + (h*1.02f - h*horizon) * perspective
        return x to y
    }

    private fun drawThing(c: Canvas, o: Thing, w: Float, h: Float) {
        if (o.z < 0f || o.z > 1.1f) return
        val (x, y) = project(o.lane.toFloat(), o.z)
        val s = (.16f + (1f-o.z)*1.55f) * w / 390f
        p.color = Color.argb(70, 0, 0, 0); c.drawOval(x-w*.055f*s, y-h*.008f*s, x+w*.055f*s, y+h*.014f*s, p)
        when (o.type) {
            Type.ROCK -> {
                p.color = Color.rgb(89, 84, 68)
                path.reset(); path.moveTo(x-w*.045f*s,y); path.lineTo(x-w*.034f*s,y-h*.045f*s); path.lineTo(x-w*.005f*s,y-h*.062f*s); path.lineTo(x+w*.04f*s,y-h*.035f*s); path.lineTo(x+w*.05f*s,y); path.close(); c.drawPath(path,p)
                p.color = Color.rgb(145, 132, 101); c.drawLine(x-w*.02f*s,y-h*.038f*s,x+w*.005f*s,y-h*.05f*s,p)
            }
            Type.LOG -> {
                p.color = Color.rgb(89, 47, 26); c.drawRoundRect(x-w*.07f*s,y-h*.032f*s,x+w*.07f*s,y, w*.012f*s,w*.012f*s,p)
                p.color = Color.rgb(185, 124, 67); c.drawCircle(x-w*.065f*s,y-h*.016f*s,w*.012f*s,p)
            }
            Type.GAP -> {
                p.color = Color.rgb(32, 28, 22); c.drawOval(x-w*.07f*s,y-h*.012f*s,x+w*.07f*s,y+h*.004f*s,p)
                p.color = Color.rgb(197, 164, 103); p.style = Paint.Style.STROKE; p.strokeWidth = w*.005f*s; c.drawOval(x-w*.07f*s,y-h*.012f*s,x+w*.07f*s,y+h*.004f*s,p); p.style = Paint.Style.FILL
            }
            Type.COIN -> Unit
        }
    }

    private fun drawCoin(c: Canvas, o: Thing, w: Float, h: Float) {
        if (o.z < 0f || o.z > 1.1f) return
        val (x, y0) = project(o.lane.toFloat(), o.z)
        val s = (.12f + (1f-o.z)*1.25f) * w / 390f
        val y = y0 - h*.025f*s
        p.color = Color.argb(55,0,0,0); c.drawOval(x-w*.018f*s,y+h*.025f*s,x+w*.018f*s,y+h*.032f*s,p)
        p.color = gold; c.drawCircle(x,y,w*.022f*s,p)
        p.color = Color.rgb(255,239,151); p.style = Paint.Style.STROKE; p.strokeWidth = max(1f,w*.004f*s); c.drawCircle(x,y,w*.015f*s,p); p.style = Paint.Style.FILL
    }

    private fun drawRunner(c: Canvas, w: Float, h: Float) {
        val groundY = h*.78f
        val x = w*.5f + visualLane*w*.25f
        val j = jump * h*.23f
        val scale = w / 390f
        val bodyY = groundY - j
        val slide = slideTimer > 0f
        // Shadow follows the runner while jumping.
        p.color = Color.argb((90 * (1f-jump.coerceAtMost(1f))).toInt(), 0,0,0)
        c.drawOval(x-w*.075f*scale,groundY-h*.012f*scale,x+w*.075f*scale,groundY+h*.005f*scale,p)
        if (slide) {
            p.color = Color.rgb(42, 34, 25); c.drawRoundRect(x-w*.09f*scale,bodyY-h*.07f*scale,x+w*.08f*scale,bodyY-h*.015f*scale,w*.025f*scale,w*.025f*scale,p)
            p.color = Color.rgb(211, 157, 92); c.drawCircle(x+w*.07f*scale,bodyY-h*.065f*scale,w*.028f*scale,p)
            p.color = Color.rgb(36, 81, 53); c.drawRoundRect(x-w*.05f*scale,bodyY-h*.025f*scale,x+w*.08f*scale,bodyY+h*.006f*scale,w*.012f*scale,w*.012f*scale,p)
        } else {
            val run = if (mode == Mode.PLAYING) kotlin.math.sin(distance*1.9f)*h*.012f else 0f
            // legs
            p.color = Color.rgb(37, 48, 37); p.strokeWidth = w*.025f*scale
            c.drawLine(x-w*.018f*scale,bodyY-h*.04f*scale,x-w*.035f*scale,bodyY+run,p)
            c.drawLine(x+w*.018f*scale,bodyY-h*.04f*scale,x+w*.035f*scale,bodyY-run,p)
            p.color = Color.rgb(72, 46, 27); c.drawRoundRect(x-w*.052f*scale,bodyY+run-h*.004f*scale,x-w*.012f*scale,bodyY+run+h*.012f*scale,w*.008f*scale,w*.008f*scale,p)
            c.drawRoundRect(x+w*.012f*scale,bodyY-run-h*.004f*scale,x+w*.052f*scale,bodyY-run+h*.012f*scale,w*.008f*scale,w*.008f*scale,p)
            // torso and arms
            p.color = Color.rgb(38, 83, 57); c.drawRoundRect(x-w*.044f*scale,bodyY-h*.12f*scale,x+w*.044f*scale,bodyY-h*.035f*scale,w*.018f*scale,w*.018f*scale,p)
            p.color = Color.rgb(191, 136, 74); p.strokeWidth = w*.018f*scale
            c.drawLine(x-w*.038f*scale,bodyY-h*.105f*scale,x-w*.075f*scale,bodyY-h*.055f*scale,p)
            c.drawLine(x+w*.038f*scale,bodyY-h*.105f*scale,x+w*.075f*scale,bodyY-h*.055f*scale,p)
            // head and headband
            p.color = Color.rgb(187, 128, 75); c.drawCircle(x,bodyY-h*.143f*scale,w*.034f*scale,p)
            p.color = Color.rgb(48, 36, 24); c.drawArc(x-w*.036f*scale,bodyY-h*.166f*scale,x+w*.036f*scale,bodyY-h*.12f*scale,180f,180f,false,p)
            p.color = gold; c.drawRect(x-w*.032f*scale,bodyY-h*.143f*scale,x+w*.032f*scale,bodyY-h*.136f*scale,p)
        }
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(170, 7, 20, 15); c.drawRoundRect(w*.045f,h*.035f,w*.43f,h*.105f, w*.025f,w*.025f,p)
        p.color = ivory; p.textSize = w*.043f; p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        c.drawText("DISTANCE  ${distance.toInt()} m",w*.07f,h*.067f,p)
        p.color = gold; c.drawText("◆  $coins",w*.07f,h*.092f,p)
        p.color = Color.argb(165,7,20,15); c.drawRoundRect(w*.69f,h*.035f,w*.955f,h*.082f,w*.02f,w*.02f,p)
        p.color = ivory; p.textSize = w*.033f; c.drawText("SPEED ${(speed*100).toInt()}%",w*.715f,h*.066f,p)
        if (mode == Mode.PLAYING && distance < 75f) {
            p.color = Color.argb(185,7,20,15); c.drawRoundRect(w*.15f,h*.86f,w*.85f,h*.935f,w*.025f,w*.025f,p)
            p.color = ivory; p.textSize = w*.036f; p.textAlign = Paint.Align.CENTER
            c.drawText("SWIPE  ←  →  TO MOVE",w*.5f,h*.89f,p)
            c.drawText("↑ JUMP     ↓ SLIDE",w*.5f,h*.918f,p); p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawOverlay(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = Color.argb(if (mode == Mode.READY) 85 else 155, 4, 16, 12); c.drawRect(0f,0f,w,h,p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BLACK)
        p.setShadowLayer(12f,0f,4f,Color.argb(120,0,0,0))
        p.color = gold; p.textSize = w*.15f; c.drawText("TEMPLE",w*.5f,h*.28f,p)
        p.color = ivory; p.textSize = w*.105f; c.drawText("RUSH",w*.5f,h*.375f,p)
        p.clearShadowLayer()
        if (mode == Mode.READY) {
            p.color = mint; p.textSize = w*.042f; c.drawText("AN ENDLESS JUNGLE RUN",w*.5f,h*.425f,p)
            drawButton(c,w*.5f,h*.58f,w*.64f,h*.075f,"TAP TO RUN")
            p.color = ivory; p.textSize = w*.036f; c.drawText("Swipe left / right to dodge",w*.5f,h*.72f,p)
            c.drawText("Swipe up to jump  •  Down to slide",w*.5f,h*.76f,p)
            p.color = Color.rgb(203,192,160); p.textSize = w*.029f; c.drawText("Collect gold. Avoid the ruins. Don't stop.",w*.5f,h*.82f,p)
        } else {
            p.color = Color.rgb(255,133,104); p.textSize = w*.055f; c.drawText("RUN ENDED",w*.5f,h*.46f,p)
            p.color = ivory; p.textSize = w*.042f; c.drawText("DISTANCE  ${distance.toInt()} m",w*.5f,h*.52f,p)
            p.color = gold; c.drawText("COINS  $coins",w*.5f,h*.565f,p)
            drawButton(c,w*.5f,h*.67f,w*.66f,h*.078f,"RUN AGAIN")
            p.color = Color.rgb(203,192,160); p.textSize = w*.031f; c.drawText("Tap anywhere to restart",w*.5f,h*.77f,p)
        }
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawButton(c: Canvas, cx: Float, cy: Float, bw: Float, bh: Float, label: String) {
        p.color = Color.rgb(34, 91, 59); c.drawRoundRect(cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,bh*.35f,bh*.35f,p)
        p.color = mint; p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; c.drawRoundRect(cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,bh*.35f,bh*.35f,p); p.style = Paint.Style.FILL
        p.color = ivory; p.textAlign = Paint.Align.CENTER; p.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD); p.textSize = bw*.105f
        c.drawText(label,cx,cy+p.textSize*.34f,p); p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; downTime=System.currentTimeMillis(); return true }
            MotionEvent.ACTION_UP -> {
                val dx = event.x-downX; val dy = event.y-downY
                if (mode != Mode.PLAYING) { startGame(); invalidate(); return true }
                if (abs(dx) < 28f && abs(dy) < 28f) {
                    // Tap controls are useful when testing on a desktop emulator.
                    val now = System.currentTimeMillis()
                    if (now-lastTapAt < 300) { if (event.x < width*.33f) lane=max(-1,lane-1) else if (event.x>width*.67f) lane=min(1,lane+1) else { jumpVelocity=.95f } }
                    lastTapAt=now
                } else if (abs(dx) > abs(dy)) {
                    if (dx < 0) lane=max(-1,lane-1) else lane=min(1,lane+1)
                    playerLean=if(dx<0) -1f else 1f
                } else {
                    if (dy < 0) { if (jump <= 0f) jumpVelocity = 1.18f }
                    else slideTimer = .72f
                }
                invalidate(); return true
            }
        }
        return true
    }
}
