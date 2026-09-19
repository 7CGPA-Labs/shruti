#ifndef SHRUTI_RING_BUFFER_HPP
#define SHRUTI_RING_BUFFER_HPP

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <span>

namespace shruti::audio {

/**
 * Single Producer Single Consumer (SPSC) Lockless Circular Ring Buffer.
 * Designed for 16 kHz 16-bit linear PCM audio streaming without dynamic heap allocation.
 */
class AudioRingBuffer {
public:
    static constexpr size_t BUFFER_SIZE = 65536; // 64 KB (2048 ms of 16kHz 16-bit mono audio)
    static constexpr size_t BUFFER_MASK = BUFFER_SIZE - 1;

    AudioRingBuffer() noexcept;
    ~AudioRingBuffer() noexcept;

    // Non-copyable, non-movable for thread safety
    AudioRingBuffer(const AudioRingBuffer&) = delete;
    AudioRingBuffer& operator=(const AudioRingBuffer&) = delete;

    /** Enqueue audio samples (Producer thread). Returns actual samples written. */
    size_t push(std::span<const int16_t> data) noexcept;

    /** Dequeue audio samples (Consumer thread). Returns actual samples read. */
    size_t pop(std::span<int16_t> output) noexcept;

    /** Clear ring buffer and securely wipe memory contents */
    void clear_and_wipe() noexcept;

    /** Returns number of available samples for reading */
    [[nodiscard]] size_t available_read() const noexcept;

    /** Returns available space for writing in samples */
    [[nodiscard]] size_t available_write() const noexcept;

private:
    alignas(64) std::atomic<size_t> write_index_{0};
    alignas(64) std::atomic<size_t> read_index_{0};
    alignas(64) int16_t buffer_[BUFFER_SIZE / sizeof(int16_t)];
};

} // namespace shruti::audio

#endif // SHRUTI_RING_BUFFER_HPP
