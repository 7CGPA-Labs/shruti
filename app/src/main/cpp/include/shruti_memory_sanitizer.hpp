#ifndef SHRUTI_MEMORY_SANITIZER_HPP
#define SHRUTI_MEMORY_SANITIZER_HPP

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace shruti::memory {

/**
 * Securely overwrites PCM audio memory buffer in volatile RAM to prevent forensic extraction.
 */
inline void wipe_pcm_frame(int16_t* buffer, size_t sample_count) noexcept {
    if (buffer == nullptr || sample_count == 0) return;

    volatile int16_t* ptr = buffer;
    size_t count = sample_count;
    while (count--) {
        *ptr++ = 0;
    }

    const size_t byte_count = sample_count * sizeof(int16_t);
    #if defined(__STDC_LIB_EXT1__)
    memset_s(buffer, byte_count, 0, byte_count);
    #else
    __asm__ __volatile__("" : : "r"(buffer) : "memory");
    #endif
}

} // namespace shruti::memory

#endif // SHRUTI_MEMORY_SANITIZER_HPP
