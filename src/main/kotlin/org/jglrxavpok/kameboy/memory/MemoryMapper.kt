package org.jglrxavpok.kameboy.memory

import org.jglrxavpok.kameboy.Gameboy
import org.jglrxavpok.kameboy.helpful.asAddress
import org.jglrxavpok.kameboy.memory.specialRegs.*
import org.jglrxavpok.kameboy.memory.specialRegs.sound.*
import org.jglrxavpok.kameboy.memory.specialRegs.video.CgbPaletteData
import org.jglrxavpok.kameboy.memory.specialRegs.video.CgbPaletteIndex
import org.jglrxavpok.kameboy.memory.specialRegs.video.Hdma5
import org.jglrxavpok.kameboy.memory.specialRegs.video.VramSelect
import org.jglrxavpok.kameboy.processing.video.PaletteMemory
import org.jglrxavpok.kameboy.sound.Sound
import org.jglrxavpok.kameboy.processing.video.SpriteAttributeTable
import org.jglrxavpok.kameboy.processing.video.Video
import org.jglrxavpok.kameboy.time.SaveStateElement

/**
 * TODO: Decouple sound/interrupts/gbc registers from MMU
 */
class MemoryMapper(val gameboy: Gameboy): MemoryComponent {

    override val name = "Memory mapper (internal)"

    var booting = gameboy.cartridge.hasBootRom

    val interruptManager = InterruptManager(this)
    val sound = Sound(this)

    @SaveStateElement
    val interruptEnableRegister = Register("Interrupt Enable Register")
    @SaveStateElement
    val lyRegister = LYRegister(this)
    @SaveStateElement
    val divRegister = DivRegister(this)
    @SaveStateElement
    val timerRegister = TimerRegister(this)

    val serialIO = SerialIO(interruptManager, this, gameboy.outputSerial)
    @SaveStateElement
    val serialControlReg = SerialControllerRegister(serialIO)
    @SaveStateElement
    val serialDataReg = SerialDataRegister(serialIO, serialControlReg)

    @SaveStateElement
    var currentSpeedFactor: Int = 1
    @SaveStateElement
    val speedRegister = SpeedRegister(this)

    @SaveStateElement
    val dma = DMARegister(this)
    val ioPorts = arrayOf(
            P1Register(gameboy.input), // $FF00
            serialDataReg, // $FF01
            serialControlReg, // $FF02
            UnknownRegister(0xFF03), // $FF03
            divRegister, // $FF04
            timerRegister, // $FF05
            Register("TMA"), // $FF06
            TacRegister(gameboy), // $FF07
            UnknownRegister(0xFF08), // $FF08
            UnknownRegister(0xFF09), // $FF09
            UnknownRegister(0xFF0A), // $FF0A
            UnknownRegister(0xFF0B), // $FF0B
            UnknownRegister(0xFF0C), // $FF0C
            UnknownRegister(0xFF0D), // $FF0D
            UnknownRegister(0xFF0E), // $FF0E
            IFRegister(), // $FF0F
            NRRegister(1,0, 0x80, sound), // $FF10
            NRx1(sound.channel1, 0x3F, sound), // $FF11
            NRRegister(1, 2, 0x00, sound), // $FF12
            NRRegister(1, 3, 0xFF, sound), // $FF13
            NRRegister(1, 4, 0xBF, sound), // $FF14
            SoundRegister(0xFF15, sound), // $FF15
            NRx1(sound.channel2, 0x3F, sound), // $FF16
            NRRegister(2, 2, 0x00, sound), // $FF17
            NRRegister(2, 3, 0xFF, sound), // $FF18
            NRRegister(2, 4, 0xBF, sound), // $FF19
            NRRegister(3, 0, 0x7F, sound), // $FF1A
            NRx1(sound.channel3, 0xFF, sound), // $FF1B
            NRRegister(3, 2, 0x9F, sound), // $FF1C
            NRRegister(3, 3, 0xFF, sound), // $FF1D
            NRRegister(3, 4, 0xBF, sound), // $FF1E
            SoundRegister(0xFF1F, sound), // $FF1F
            NRx1(sound.channel4, 0xFF, sound), // $FF20
            NRRegister(4, 2, 0x00, sound), // $FF21
            NRRegister(4, 3, 0x00, sound), // $FF22
            NRRegister(4, 4, 0xBF, sound), // $FF23
            sound.channelControl, // $FF24
            sound.outputSelect, // $FF25
            sound.soundToggle, // $FF26
            SoundRegister(0xFF27, sound), // $FF27
            SoundRegister(0xFF28, sound), // $FF28
            SoundRegister(0xFF29, sound), // $FF29
            SoundRegister(0xFF2A, sound), // $FF2A
            SoundRegister(0xFF2B, sound), // $FF2B
            SoundRegister(0xFF2C, sound), // $FF2C
            SoundRegister(0xFF2D, sound), // $FF2D
            SoundRegister(0xFF2E, sound), // $FF2E
            SoundRegister(0xFF2F, sound), // $FF2F
            MemoryRegister("Wave Pattern RAM 0", this, 0xFF30), // $FF30
            MemoryRegister("Wave Pattern RAM 1", this, 0xFF31), // $FF31
            MemoryRegister("Wave Pattern RAM 2", this, 0xFF32), // $FF32
            MemoryRegister("Wave Pattern RAM 3", this, 0xFF33), // $FF33
            MemoryRegister("Wave Pattern RAM 4", this, 0xFF34), // $FF34
            MemoryRegister("Wave Pattern RAM 5", this, 0xFF35), // $FF35
            MemoryRegister("Wave Pattern RAM 6", this, 0xFF36), // $FF36
            MemoryRegister("Wave Pattern RAM 7", this, 0xFF37), // $FF37
            MemoryRegister("Wave Pattern RAM 8", this, 0xFF38), // $FF38
            MemoryRegister("Wave Pattern RAM 9", this, 0xFF39), // $FF39
            MemoryRegister("Wave Pattern RAM A", this, 0xFF3A), // $FF3A
            MemoryRegister("Wave Pattern RAM B", this, 0xFF3B), // $FF3B
            MemoryRegister("Wave Pattern RAM C", this, 0xFF3C), // $FF3C
            MemoryRegister("Wave Pattern RAM D", this, 0xFF3D), // $FF3D
            MemoryRegister("Wave Pattern RAM E", this, 0xFF3E), // $FF3E
            MemoryRegister("Wave Pattern RAM F", this, 0xFF3F), // $FF3F
            Register("LCDC"), // $FF40
            OrOnReadRegister("STAT", 0b1000_0000), // $FF41
            Register("SCY"), // $FF42
            Register("SCX"), // $FF43
            lyRegister, // $FF44
            Register("LYC"), // $FF45
            dma, // $FF46
            Register("BGP"), // $FF47
            Register("OBP0"), // $FF48
            Register("OBP1"), // $FF49
            Register("WY"), // $FF4A
            Register("WX") // $FF4B
    )

    val undocumented6C = OrOnReadRegister("FF6C", 0xFE)
    val undocumented72 = Register("FF72")
    val undocumented73 = Register("FF73")
    val undocumented74 = Register("FF74")
    val undocumented75 = OrOnReadRegister("FF75", 0x8F)
    val undocumented76 = object: Register("FF76") {
        override fun read(address: Int): Int {
            return 0x00
        }
    }
    val undocumented77 = object: Register("FF77 ") {
        override fun read(address: Int): Int {
            return 0x00
        }
    }

    @SaveStateElement
    val spriteAttributeTable = SpriteAttributeTable()
    @SaveStateElement
    val empty0 = object: RAM(0xFF00-0xFEA0) {
        override val name = "Empty0"

        override fun correctAddress(address: Int): Int {
            if(gameboy.isCGB) {

                /*
                from The Cycle Accurate Gameboy Docs:
                "- CGB: There are another 32 bytes at FEA0h – FEBFh. At FEC0h – FECFh there are another 16
                bytes that are repeated in FED0h – FEDFh, FEE0h – FEEFh and FEF0h – FEFFh. Reading and
                writing to any of this 4 blocks will change the same 16 bytes.
                FEA0h 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F
                FEB0h 10 11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F
                FEC0h 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F
                FED0h 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F
                FEE0h 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F
                FEF0h 20 21 22 23 24 25 26 27 28 29 2A 2B 2C 2D 2E 2F"
                 */

                val off = address - 0xFEA0
                if(off > 0x1F) {
                    return (off % 0x0F) + 0x20
                }
                return off
            }
            return 0
        }

        override fun write(address: Int, value: Int) {
            if(gameboy.isCGB)
                super.write(address, value)
        }

        override fun read(address: Int): Int {
            if( ! gameboy.isCGB)
                return 0x00 // always 00h on DMG
            return super.read(address)
        }
    }
    @SaveStateElement
    val empty1 = object: RAM(0xFF80-0xFF4C) {
        override val name = "Empty1"

        override fun correctAddress(address: Int): Int {
            return address-0xFF4C
        }

        override fun read(address: Int): Int {
            return 0xFF//super.read(address)
        }
    }
    @SaveStateElement
    val internalRAM = object: RAM(8*1024) {
        override val name = "Internal RAM"

        override fun correctAddress(address: Int): Int {
            if(address >= 0xE000) // handle echo of internal RAM
                return address-0xE000
            return address-0xC000
        }
    }
    @SaveStateElement
    val highRAM = object: RAM(0xFFFE-0xFF80 +1) {
        override val name = "High RAM"

        override fun correctAddress(address: Int): Int {
            return address-0xFF80
        }
    }
    @SaveStateElement
    val vram0 = object: RAM(8*1024) {
        override val name = "Video RAM #0"

        override fun correctAddress(address: Int): Int {
            return address-0x8000
        }
    }
    @SaveStateElement
    val vram1 = object: RAM(8*1024) {
        override val name = "Video RAM #1"

        override fun correctAddress(address: Int): Int {
            return address-0x8000
        }
    }
    val bootRegister = object : MemoryComponent {
        override val name = "Boot register"

        override fun write(address: Int, value: Int) {
            if(value and 0x1 == 0x1)
                booting = false
        }

        override fun read(address: Int): Int {
            return 0xFF
        }
    }
    @SaveStateElement
    val wavePatternRam = WavePatternRam(sound)
    @SaveStateElement
    val wramBanks = Array<RAM>(8) { index ->
        object: RAM(0x4000) {
            override val name: String
                get() = "WRAM Bank #$index"

            override fun correctAddress(address: Int): Int {
                if(index == 0)
                    return address-0xC000
                return address-0xD000
            }
        }
    }
    val wramBankSelect = WramBankSelectRegister()
    val infraredRegister = InfraredRegister()

    @SaveStateElement
    val backgroundPaletteMemory = PaletteMemory("Background")
    @SaveStateElement
    val spritePaletteMemory = PaletteMemory("Sprite")

    val backgroundPaletteIndex = CgbPaletteIndex("Background Palette Index")
    @SaveStateElement
    val backgroundPaletteData = CgbPaletteData("Background Palette Data", backgroundPaletteIndex, backgroundPaletteMemory)

    val spritePaletteIndex = CgbPaletteIndex("Sprite Palette Index")
    @SaveStateElement
    val spritePaletteData = CgbPaletteData("Sprite Palette Data", spritePaletteIndex, spritePaletteMemory)

    val vramSelect = VramSelect()

    val hdma1 = OrOnReadRegister("HDMA 1", orValue = 0xFF)
    val hdma2 = OrOnReadRegister("HDMA 2", orValue = 0xFF)
    val hdma3 = OrOnReadRegister("HDMA 3", orValue = 0xFF)
    val hdma4 = OrOnReadRegister("HDMA 4", orValue = 0xFF)
    val hdma5 = Hdma5(this)

    fun stepBeforeTimer(cycles: Int) {
        timerRegister.step(cycles) // TIMA contains 00 for 4 cycles after a reset (Mooneye GB)
    }

    fun step(cycles: Int) {
        dma.step(cycles)
        hdma5.step(cycles)
    }

    inline fun map(address: Int): MemoryComponent {
        if(gameboy.inCGBMode) {
            val comp = when(address.asAddress()) {
                // GBC registers
                0xFF4D -> speedRegister
                0xFF56 -> infraredRegister
                0xFF70 -> wramBankSelect
                0xFF68 -> backgroundPaletteIndex
                0xFF69 -> when(gameboy.video.mode) { Video.VideoMode.Mode3 -> UnaccessibleMemory
                    else -> backgroundPaletteData
                }
                0xFF6A -> spritePaletteIndex
                0xFF6B -> when(gameboy.video.mode) { Video.VideoMode.Mode3 -> UnaccessibleMemory
                    else -> spritePaletteData
                }
                0xFF4F -> vramSelect
                0xFF51 -> hdma1
                0xFF52 -> hdma2
                0xFF53 -> hdma3
                0xFF54 -> hdma4
                0xFF55 -> hdma5
                in 0x8000 until 0xA000 ->
                    if(gameboy.video.mode == Video.VideoMode.Mode3) {
                        UnaccessibleMemory
                    } else {
                        if(vramSelect[0]) vram1 else vram0
                    }
                else -> this
            }
            if(comp != this)
                return comp
        }
        return when(address.asAddress()) {
        // DMG registers
            in 0 until 0x8000 -> {
                if(booting && gameboy.cartridge.hasBootRom && address in 0 until gameboy.cartridge.bootROM!!.size && address !in 0x100..0x1FF) {
                    gameboy.cartridge.bootRomComponent
                } else {
                    gameboy.cartridge
                }
            }
            in 0x8000 until 0xA000 -> {
                if(gameboy.video.mode == Video.VideoMode.Mode3) {
                    UnaccessibleMemory
                } else {
                    vram0
                }
            }
            in 0xA000 until 0xC000 -> {
                if(gameboy.cartridge.cartrigeType.accepts(address)) {
                    gameboy.cartridge.cartrigeType
                } else {
                    if(gameboy.cartridge.ramBankCount != 0)
                        gameboy.cartridge.currentRAMBank
                    else
                        UnaccessibleMemory
                }
            }
            in 0xC000..0xCFFF, in 0xE000..0xEFFF -> {
                if(gameboy.inCGBMode) {
                    wramBanks[0]
                } else {
                    internalRAM
                }
            }
            in 0xD000..0xDFFF, in 0xF000..0xFDFF -> {
                when {
                    gameboy.inCGBMode -> {
                        val bankIndex = wramBankSelect.getValue()
                        if(bankIndex == 0)
                            wramBanks[1]
                        else
                            wramBanks[bankIndex]
                    }
                    else -> internalRAM
                }
            }
            in 0xFE00 until 0xFEA0 -> when(gameboy.video.mode) {
                Video.VideoMode.Mode2, Video.VideoMode.Mode3 -> UnaccessibleMemory
                else -> spriteAttributeTable
            }
            in 0xFEA0 until 0xFF00 -> empty0
            in 0xFF30..0xFF3F -> wavePatternRam
            in 0xFF00 until 0xFF4C -> ioPorts[address-0xFF00]
            in 0xFF4C until 0xFF80 -> {
                when {
                    booting && address == 0xFF50 -> bootRegister
                    // undocumented but writable adddresses
                    gameboy.isCGB && address == 0xFF6C && gameboy.inCGBMode -> undocumented6C
                    gameboy.isCGB && address == 0xFF72 -> undocumented72
                    gameboy.isCGB && address == 0xFF73 -> undocumented73
                    gameboy.isCGB && address == 0xFF74 && gameboy.inCGBMode -> undocumented74
                    gameboy.isCGB && address == 0xFF75 -> undocumented75
                    gameboy.isCGB && address == 0xFF76 -> undocumented76
                    gameboy.isCGB && address == 0xFF77 -> undocumented77
                    else -> empty1
                }
            }
            in 0xFF80 until 0xFFFF -> highRAM
            0xFFFF -> interruptEnableRegister

            else -> error("Invalid address ${Integer.toHexString(address)}")
        }
    }

    override fun write(address: Int, value: Int) {
        map(address).write(address, value)
    }
    override fun read(address: Int) = map(address).read(address)
}
