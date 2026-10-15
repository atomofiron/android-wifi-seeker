package ru.raslav.wirelessscan.data

// 17 bits - frequency 0..69120
// 8 bits - channel 0..233
// 3 bits - band 0..7

@JvmInline
value class FrequencyChannelBand private constructor(val value: Int) {

    val frequency: Int get() = value and FREQUENCY_MASK
    val channel: Int get() = value shr FREQUENCY_LENGTH and CHANNEL_MASK
    val band: Band? get() = Band.entries.getOrNull(value.shr(FREQUENCY_LENGTH + CHANNEL_LENGTH).dec())

    companion object {

        private const val FREQUENCY_LENGTH = 17
        private const val FREQUENCY_MASK = 0b1_1111_1111_1111_1111
        private const val CHANNEL_LENGTH = 8
        private const val CHANNEL_MASK = 0b1111_1111

        operator fun invoke(frequency: Int) = FrequencyChannelBand(frequency or getChannelWithBand(frequency).shl(FREQUENCY_LENGTH))

        fun getChannelWithBand(frequency: Int): Int = when (frequency) {
            in 2412..2472 -> (frequency - 2412) / 5 + 1 or 1.shl(CHANNEL_LENGTH)
            2484 -> 14 or 1.shl(CHANNEL_LENGTH)
            in 3655..3695 -> 131 + (frequency - 3655) / 5 or 2.shl(CHANNEL_LENGTH)
            in 4910..4980 -> (frequency - 4000) / 5 or 3.shl(CHANNEL_LENGTH)
            in 5035..5905 -> (frequency - 5000) / 5 or 4.shl(CHANNEL_LENGTH)
            5935 -> 2 or 5.shl(CHANNEL_LENGTH)
            in 5955..7115 -> (frequency - 5955) / 5 + 1 or 6.shl(CHANNEL_LENGTH)
            in 58320..69120 -> (frequency - 58320) / 2160 + 1 or 7.shl(CHANNEL_LENGTH)
            else -> 0
        }
    }
}