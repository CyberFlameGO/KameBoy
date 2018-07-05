package org.jglrxavpok.kameboy

import org.jglrxavpok.kameboy.helpful.*
import org.jglrxavpok.kameboy.hooks.InterruptHandlerEvent
import org.jglrxavpok.kameboy.input.PlayerInput
import org.jglrxavpok.kameboy.memory.Cartridge
import org.jglrxavpok.kameboy.network.guest.GuestSession
import org.jglrxavpok.kameboy.network.host.Server
import org.jglrxavpok.kameboy.processing.Instructions
import org.jglrxavpok.kameboy.processing.video.Palettes
import org.jglrxavpok.kameboy.ui.*
import org.jglrxavpok.kameboy.ui.options.CheatingOptions
import org.jglrxavpok.kameboy.ui.options.GraphicsOptions
import org.jglrxavpok.kameboy.ui.options.OptionsWindow
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glGenVertexArrays
import java.awt.Toolkit
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import javax.swing.*

class KameboyCore(val args: Array<String>): PlayerInput, GameboyControls {
    private var window: Long

    val cartridge = _DEV_cart("Pokemon Cristal.gbc", useBootRom = true)
    val outputSerial = "-outputserial" in args
    var core: EmulatorCore = NoGameCore
    private var shaderID: Int
    private var textureID: Int
    private var meshID: Int
    private var diffuseTextureUniform: Int
    val audioSystem: KameboyAudio
    private var paletteIndex = 0
    private val joysticks = Array(10, ::Joystick)
    private val messageSystem = MessageSystem()
    private var fastForward = false
    private val noGameImage =
            ImageIO.read(javaClass.getResourceAsStream("/images/no_game.png"))
                    .getRGB(0,0,256,256,null,0, 256).apply {
                        for(index in 0 until size) {
                            val color = this[index]
                            val red = (color shr 16) and 0xFF
                            val green = (color shr 8) and 0xFF
                            val blue = color and 0xFF
                            this[index] = (blue shl 16) or (green shl 8) or red
                        }
                    }

    companion object {
        lateinit var CoreInstance: KameboyCore
    }

    init {
        CoreInstance = this
        Config.load()
        val scale = 6
        window = glfwCreateWindow(160*scale, 144*scale, "Kameboy (${core.title})", nullptr, nullptr)
        glfwSetWindowAspectRatio(window, 160, 144)
        initInput()
        positionWindows()
        glfwShowWindow(window)

        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        shaderID = LoadShader("blit")
        glUseProgram(shaderID)
        diffuseTextureUniform = glGetUniformLocation(shaderID, "diffuse")
        glUniform1i(diffuseTextureUniform, 0)
        FontRenderer.init()
        textureID = prepareTexture()
        meshID = prepareRenderMesh()
        audioSystem = KameboyAudio(core.gameboy.mapper.sound)

        runEmulator()
        cleanup()
    }

    private fun positionWindows() {
        GraphicsOptions.paletteSelection.addActionListener { _ ->
            val index = GraphicsOptions.paletteSelection.selectedIndex
            changePalette(index)
        }

        val width = IntArray(1)
        val height = IntArray(1)
        glfwGetWindowSize(window, width, height)
        val spacing = 5
        val videoMode = Toolkit.getDefaultToolkit().screenSize
        val x = videoMode.width/2-width[0]/2
        val y = videoMode.height/2-height[0]/2

        glfwSetWindowPos(window, x, y)
        OptionsWindow.setLocation(x+width[0]+spacing, y)
        OptionsWindow.isVisible = true

        EmulatorControlWindow.setLocation(x-EmulatorControlWindow.width-spacing, y)
        EmulatorControlWindow.isVisible = true
    }

    fun getScreenSize(): Pair<Int, Int> {
        val width = IntArray(1)
        val height = IntArray(1)
        glfwGetWindowSize(window, width, height)
        return Pair(width[0], height[0])
    }

    private fun prepareRenderMesh(): Int {
        val vaoID = glGenVertexArrays()
        val vertexBufferID = glGenBuffers()

        val indexBufferID = glGenBuffers()
        glBindVertexArray(vaoID)

        glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBufferID)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2*4, 0)

        val vertexBuffer = BufferUtils.createFloatBuffer(4*(3+2))

        vertexBuffer.put(floatArrayOf(0f, 0f))

        vertexBuffer.put(floatArrayOf(1f, 0f))

        vertexBuffer.put(floatArrayOf(1f, 1f))

        vertexBuffer.put(floatArrayOf(0f, 1f))

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBufferID)
        val indexBuffer = BufferUtils.createIntBuffer(6)
        indexBuffer.put(0).put(1).put(2)
        indexBuffer.put(2).put(3).put(0)

        vertexBuffer.flip()
        indexBuffer.flip()
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW)
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_DYNAMIC_DRAW)

        return vaoID
    }

    private fun prepareTexture(): Int {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?)
        return id
    }

    private fun initInput() {
        glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
            if(action == GLFW_RELEASE) {
                when(key) {
                    GLFW_KEY_F1 -> core.dumpInfos()
                    GLFW_KEY_F2 -> core.showBGMap()
                    GLFW_KEY_F3 -> showMemoryContents()
                    GLFW_KEY_F -> { fastForward = false }

                    in GLFW_KEY_1..GLFW_KEY_9 -> {
                        val shiftPressed = mods and GLFW_MOD_SHIFT != 0
                        val index = (key-GLFW_KEY_1)+1
                        if(shiftPressed) {
                            core.loadSaveState(index)
                        } else {
                            core.createSaveState(index)
                        }
                    }
                    GLFW_KEY_0 -> {
                        val shiftPressed = mods and GLFW_MOD_SHIFT != 0
                        if(shiftPressed) {
                            core.loadSaveState(0)
                        } else {
                            core.createSaveState(0)
                        }
                    }
                    GLFW_KEY_PAGE_UP -> {
                        changePalette(paletteIndex-1)
                    }

                    GLFW_KEY_PAGE_DOWN -> {
                        changePalette(paletteIndex+1)
                    }
                }
            } else {
                when(key) {
                    GLFW_KEY_F -> { fastForward = true }
                }
            }
            val bit = when(key) {
                GLFW_KEY_RIGHT, GLFW_KEY_Q -> 0
                GLFW_KEY_LEFT, GLFW_KEY_W -> 1
                GLFW_KEY_UP, GLFW_KEY_BACKSPACE -> 2
                GLFW_KEY_DOWN, GLFW_KEY_ENTER -> 3
                else -> return@glfwSetKeyCallback
            }
            val released = when(action) {
                GLFW_PRESS -> 0
                GLFW_RELEASE -> 1
                else -> return@glfwSetKeyCallback
            }
            when {
                isButtonKey(key) -> buttonState = buttonState.setBits(released, bit..bit)
                isDirectionKey(key) -> directionState = directionState.setBits(released, bit..bit)
            }
            if(released == 0)
                core.gameboy.interruptManager.firePinPressed()
        }

        glfwSetJoystickCallback { id, event ->
            if(event == GLFW_CONNECTED) {
                println("Joystick $id connected, name is ${glfwGetJoystickName(id)}")
                joysticks[id].connected = true
            } else if(event == GLFW_DISCONNECTED) {
                println("Joystick $id disconnected")
                joysticks[id].connected = false
            }
        }
    }

    private fun changePalette(newIndex: Int) {
        paletteIndex = newIndex
        if(paletteIndex >= Palettes.size)
            paletteIndex = 0
        if(paletteIndex < 0)
            paletteIndex = Palettes.size-1

        GraphicsOptions.paletteSelection.selectedIndex = paletteIndex
        GraphicsOptions.paletteSelection.repaint()
        core.gameboy.video.dmgPalette = Palettes[paletteIndex]
        println("Now using palette $paletteIndex")
    }

    private fun showMemoryContents() {
        val frame by lazy { JFrame() }
        frame.contentPane.removeAll()
        val linesPanel = JPanel()
        linesPanel.layout = BoxLayout(linesPanel, BoxLayout.Y_AXIS)

        var address = 0
        while(address <= 0xFFFF) {
            val areaType = when(address) {
                0xFFFF, 0xFF50, 0xFF4D -> Instructions.AreaType.SpecialRegister
                in 0x8000 until 0xA000 -> Instructions.AreaType.VideoRam
                in 0xA000 until 0xC000 -> Instructions.AreaType.Ram
                in 0xFF00 until 0xFF4C -> Instructions.AreaType.IO

                else -> Instructions.AreaType.Rom
            }
            val content = Instructions.readInstruction(core.gameboy.mapper, address, areaType)
            linesPanel.add(JLabel(content.desc))
            address += content.size
        }
        frame.contentPane.add(JScrollPane(linesPanel))
        frame.repaint()
        frame.isVisible = true
    }

    private fun isButtonKey(key: Int) = key in arrayOf(GLFW_KEY_Q, GLFW_KEY_W, GLFW_KEY_ENTER, GLFW_KEY_BACKSPACE)

    private fun isDirectionKey(key: Int) = key in arrayOf(GLFW_KEY_UP, GLFW_KEY_DOWN, GLFW_KEY_LEFT, GLFW_KEY_RIGHT)
    override var buttonState = 0b1111

    override var directionState = 0b1111
    private fun _DEV_cart(name: String, useBootRom: Boolean = false): Cartridge {
        val saveFolder = File("./saves/")
        if(!saveFolder.exists())
            saveFolder.mkdirs()
        val romContents = _DEV_rom(name)
        val bootRom = if(useBootRom) {
            val isOnlyForColorGB = romContents[0x0143].asUnsigned() == 0xC0
            val isForColorGB = romContents[0x0143].asUnsigned() == 0x80 || isOnlyForColorGB
            _DEV_BOOT_ROM(isForColorGB)
        } else {
            null
        }
        return Cartridge(romContents, bootRom, File(saveFolder, name.takeWhile { it != '.' }+".sav"))
    }

    private fun _DEV_rom(name: String) = KameboyCore::class.java.getResourceAsStream("/roms/$name").buffered().use { it.readBytes() }
    private fun _DEV_BOOT_ROM(cgb: Boolean): ByteArray? {
        val filename = if(cgb) "CGB_ROM.bin" else "DMG_ROM.bin"
        val bootRomFile = File(filename)
        if(!bootRomFile.exists())
            return null
        return bootRomFile.readBytes()
    }

    private fun cleanup() {
        Config.save()
        GuestSession.disconnect()
        Server.stop()
        OptionsWindow.dispose()
        EmulatorControlWindow.dispose()
        audioSystem.cleanup()
        glDeleteProgram(shaderID)
        glfwDestroyWindow(window)
        glfwTerminate()
    }

    private fun runEmulator() {
        core.init()
        audioSystem.start()
        val windowWPointer = IntArray(1)
        val windowHPointer = IntArray(1)
        glfwSwapInterval(0)

        var time = glfwGetTime()
        var frames = 0
        var totalTime = 0.0
        val videoSyncTime = if(cartridge.isForColorGB) EmulatorCore.CGBVideoVSync else EmulatorCore.DMGVideoVSync
        val optimalTime = 1f/videoSyncTime
        var lastTime = glfwGetTime()-optimalTime
        glClearColor(0f, .8f, 0f, 1f)

        glActiveTexture(GL_TEXTURE0)

        while(!glfwWindowShouldClose(window)) {
            val delta = glfwGetTime()-lastTime
            lastTime = glfwGetTime()
            glfwGetWindowSize(window, windowWPointer, windowHPointer)
            pollEvents()
            glClear(GL_COLOR_BUFFER_BIT)
            glViewport(0, 0, windowWPointer[0], windowHPointer[0])

            messageSystem.step(delta.toFloat())
            val catchupSpeed = (delta/optimalTime).coerceIn(1.0/1000.0 .. 6.0) // between 10 fps and 1000fps
            if(core === NoGameCore) {
                updateTexture(core, noGameImage)
            } else { // render actual emulator
                if(fastForward) {
                    core.frame(5.0)
                } else {
                    core.frame(catchupSpeed)
                }
            }

            glBindTexture(GL_TEXTURE_2D, textureID)
            glUseProgram(shaderID)

            glBindVertexArray(meshID)
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)

            messageSystem.drawMessages()

            glfwSwapBuffers(window)

            val newTime = glfwGetTime()
            val deltaTime = newTime-time
            time = newTime
            frames++
            totalTime += deltaTime
            if(totalTime >= 1f) {
                if(outputSerial)
                    println("$frames fps")
                frames = 0
                totalTime %= 1f
            }
        }
    }

    private fun pollEvents() {
        glfwPollEvents()
        joysticks.filter { glfwJoystickPresent(it.id) }
                .forEach {
                    it.savePreviousState()
                    it.connected = true
                    it.buttons = glfwGetJoystickButtons(it.id)!!
                    it.axes = glfwGetJoystickAxes(it.id)!!
                    it.hats = glfwGetJoystickHats(it.id)!!

                    tailrec fun checkJoystickChanges() {
                        val change = it.findFirstIntersection()
                        if(change != null) {
                            when(change.second) {
                                Joystick.Component.AXIS -> {
                                    val axis = change.first
                                    val axisValue = it.axis(axis)

                                    handleAxisChange(axis, axisValue, it)
                                }

                                Joystick.Component.BUTTON -> {
                                    val button = change.first
                                    val buttonState = it.button(button)
                                    handleButtonChange(button, buttonState, it)
                                }
                            }
                            checkJoystickChanges()
                        }
                    }
                    checkJoystickChanges()
                }
    }

    private fun handleAxisChange(axis: Int, axisValue: Float, joystick: Joystick) {
        // TODO: input remapping

        GetControllerMapping(joystick).handleAxisChange(axis, axisValue, this)
    }



    private fun handleButtonChange(button: Int, buttonState: Boolean, joystick: Joystick) {
        // TODO: input remapping

        GetControllerMapping(joystick).handleButtonChange(button, buttonState, this)
    }

    fun updateTexture(core: EmulatorCore, videoData: IntArray) {
        val data by lazy { IntArray(256*256) }
        glBindTexture(GL_TEXTURE_2D, textureID)
        for((index, color) in videoData.withIndex()) {
            val correctFormatColor = color and 0xFFFFFF
            data[index] = correctFormatColor // or 0xFF
        }
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 256, GL_RGBA, GL_UNSIGNED_BYTE, data)
    }

    fun loadROM(file: File) {
        val romContents = file.readBytes()
        val useBootRom = true // TODO: config
        val bootRom = if(useBootRom) {
            val isOnlyForColorGB = romContents[0x0143].asUnsigned() == 0xC0
            val isForColorGB = romContents[0x0143].asUnsigned() == 0x80 || isOnlyForColorGB
            _DEV_BOOT_ROM(isForColorGB)
        } else {
            null
        }
        val saveFolder = File("./saves/")
        if(!saveFolder.exists())
            saveFolder.mkdirs()
        val saveFile = File(saveFolder, file.name.takeWhile { it != '.' }+".sav")
        val cart = Cartridge(romContents, bootRom, saveFile)
        changeCore(EmulatorCore(cart, this, outputSerial, renderRoutine = { pixels -> updateTexture(this /* emulator core */, pixels) }, messageSystem = messageSystem))
        core.init()
        audioSystem.reloadGBSound(core.gameboy.mapper.sound)
        updateTitle()
        messageSystem.message("Started ${core.title}")

        EmulatorControlWindow.ejectCartridge.isEnabled = true
        EmulatorControlWindow.resetGame.isEnabled = true
    }

    private fun updateTitle() {
        glfwSetWindowTitle(window, "KameBoy (${core.title})")
    }

    fun ejectCartridge() {
        messageSystem.message("Ejected ${core.title}")

        changeCore(NoGameCore)
        audioSystem.reloadGBSound(core.gameboy.mapper.sound)
        updateTitle()

        EmulatorControlWindow.ejectCartridge.isEnabled = false
        EmulatorControlWindow.resetGame.isEnabled = false
    }

    fun hardReset() {
        val cart = Cartridge(core.cartridge.rawData, core.cartridge.bootROM, core.cartridge.saveFile)
        changeCore(EmulatorCore(cart, this, outputSerial, renderRoutine = { pixels -> updateTexture(this /* emulator core */, pixels) }, messageSystem = messageSystem))
        core.init()
        audioSystem.reloadGBSound(core.gameboy.mapper.sound)
        updateTitle()

        messageSystem.message("Hard reset ${core.title}")
    }

    private fun changeCore(newCore: EmulatorCore) {
        core = newCore
        newCore.gameboy.hooks.registerHookHandler(::handleGamesharkCheats)
    }

    private fun handleGamesharkCheats(event: InterruptHandlerEvent) {
        if(event.interruptIndex != 0) // V-Blank
            return
        val codes = CheatingOptions.gamesharkCodes
        val cart = core.gameboy.cartridge
        val ramBanks = cart.ramBanks
        for(code in codes) {
            val currentRamBank = cart.selectedRAMBankIndex
            if(code.externalRamBankNumber < ramBanks.size) {
                cart.selectedRAMBankIndex = code.externalRamBankNumber
            }
            core.gameboy.mapper.write(code.memoryAddress, code.newData)
            if(code.externalRamBankNumber < ramBanks.size) {
                cart.selectedRAMBankIndex = currentRamBank
            }
        }
    }

    override fun pressA() {
        core.gameboy.interruptManager.firePinPressed()
        buttonState = buttonState.setBits(GbPressBit, 0..0)
    }

    override fun pressB() {
        core.gameboy.interruptManager.firePinPressed()
        buttonState = buttonState.setBits(GbPressBit, 1..1)
    }

    override fun releaseA() {
        buttonState = buttonState.setBits(GbReleaseBit, 0..0)
    }

    override fun releaseB() {
        buttonState = buttonState.setBits(GbReleaseBit, 1..1)
    }

    override fun pressStart() {
        core.gameboy.interruptManager.firePinPressed()
        buttonState = buttonState.setBits(GbPressBit, 3..3)
    }

    override fun releaseStart() {
        buttonState = buttonState.setBits(GbReleaseBit, 3..3)
    }

    override fun pressSelect() {
        core.gameboy.interruptManager.firePinPressed()
        buttonState = buttonState.setBits(GbPressBit, 2..2)
    }

    override fun releaseSelect() {
        buttonState = buttonState.setBits(GbReleaseBit, 2..2)
    }

    override fun pressLeft() {
        core.gameboy.interruptManager.firePinPressed()
        directionState = directionState.setBits(GbPressBit, 1..1)
    }

    override fun releaseLeft() {
        directionState = directionState.setBits(GbReleaseBit, 1..1)
    }

    override fun pressUp() {
        core.gameboy.interruptManager.firePinPressed()
        directionState = directionState.setBits(GbPressBit, 2..2)
    }

    override fun releaseUp() {
        directionState = directionState.setBits(GbReleaseBit, 2..2)
    }

    override fun pressDown() {
        core.gameboy.interruptManager.firePinPressed()
        directionState = directionState.setBits(GbPressBit, 3..3)
    }

    override fun releaseDown() {
        directionState = directionState.setBits(GbReleaseBit, 3..3)
    }

    override fun pressRight() {
        core.gameboy.interruptManager.firePinPressed()
        directionState = directionState.setBits(GbPressBit, 0..0)
    }

    override fun releaseRight() {
        directionState = directionState.setBits(GbReleaseBit, 0..0)
    }
}
