package com.homm3.livewallpaper.core

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.homm3.livewallpaper.core.Constants.Companion.TILE_SIZE
import com.homm3.livewallpaper.parser.formats.JsonMap
import ktx.app.KtxScreen
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

class WallpaperScreen(private val engine: Engine) : KtxScreen {
    private val h3mMap = JsonMap().parse(Gdx.files.internal("maps/invasion.json").read())
    private var terrainRenderer = TerrainRenderer(engine, h3mMap)
    private var objectsRenderer = ObjectsRenderer(engine, h3mMap)
    private var inputProcessor = InputProcessor(engine.camera).apply {
        onRandomizeCameraPosition = ::randomizeCameraPosition
    }
    private var mapUpdateInterval = Constants.Preferences.DEFAULT_MAP_UPDATE_INTERVAL * 60f * 1000f
    private var lastMapUpdateTime = System.currentTimeMillis()

    init {
        engine.camera.setToOrtho(true)
        applyPreferences()
        randomizeCameraPosition()

        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            Gdx.input.inputProcessor = inputProcessor
        }
    }

    private fun applyPreferences() {
        mapUpdateInterval = Gdx.app
            .getPreferences(Constants.Preferences.PREFERENCES_NAME)
            .getInteger(Constants.Preferences.MAP_UPDATE_INTERVAL, Constants.Preferences.DEFAULT_MAP_UPDATE_INTERVAL) * 60f * 1000f

        val scale = Gdx.app
            .getPreferences(Constants.Preferences.PREFERENCES_NAME)
            .getString(Constants.Preferences.SCALE)
        engine.camera.zoom = when (scale) {
            "DPI" -> min(1 / Gdx.graphics.density, 1f)
            else -> 1 / (scale.toFloatOrNull() ?: 1f)
        }
    }

    private fun randomizeCameraPosition() {
        val camera = engine.camera

        val cameraViewportWidthTiles = ceil(camera.viewportWidth * camera.zoom / TILE_SIZE)
        val halfWidth = ceil(cameraViewportWidthTiles / 2).toInt()
        val nextCameraX = Random.nextInt(halfWidth, h3mMap.size - halfWidth) * TILE_SIZE

        val cameraViewportHeightTiles = ceil(camera.viewportHeight * camera.zoom / TILE_SIZE)
        val halfHeight = ceil(cameraViewportHeightTiles / 2).toInt()
        val nextCameraY = Random.nextInt(halfHeight, h3mMap.size - halfHeight) * TILE_SIZE

        camera.position.set(nextCameraX, nextCameraY, 0f)
    }

    override fun show() {
        applyPreferences()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMapUpdateTime >= mapUpdateInterval) {
            lastMapUpdateTime = currentTime
            randomizeCameraPosition()
        }
    }

    override fun resize(width: Int, height: Int) {
        engine.viewport.update(width, height, false)
    }

    override fun render(delta: Float) {
        engine.camera.update()
        terrainRenderer.render()
        objectsRenderer.render(delta)
    }

    override fun dispose() {
        super.dispose()
        terrainRenderer.dispose()
        objectsRenderer.dispose()
    }
}