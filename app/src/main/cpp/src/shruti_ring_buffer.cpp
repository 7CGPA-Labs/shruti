#include "shruti_ring_buffer.hpp"
#include "shruti_memory_sanitizer.hpp"
#include <algorithm>
#include <cstring>

namespace shruti::audio {

constexpr size_t TOTAL_SAMPLES = AudioRingBuffer::BUFFER_SIZE / sizeof(int16_t);

AudioRingBuffer::AudioRingBuffer() noexcept {
    clear_and_wipe();
}

AudioRingBuffer::~AudioRingBuffer() noexcept {
    clear_and_wipe();
}

size_t AudioRingBuffer::available_read() const noexcept {
    const size_t w = write_index_.load(std::memory_order_acquire);
    const size_t r = read_index_.load(std::memory_order_relaxed);
    return w - r;
}

size_t AudioRingBuffer::available_write() const noexcept {
    return TOTAL_SAMPLES - available_read();
}

size_t AudioRingBuffer::push(std::span<const int16_t> data) noexcept {
    const size_t write_avail = available_write();
    const size_t to_write = std::min(data.size(), write_avail);
    if (to_write == 0) return 0;

    const size_t current_write = write_index_.load(std::memory_order_relaxed);
    const size_t index = current_write & (TOTAL_SAMPLES - 1);
    const size_t first_part = std::min(to_write, TOTAL_SAMPLES - index);

    std::memcpy(&buffer_[index], data.data(), first_part * sizeof(int16_t));
    if (to_write > first_part) {
        std::memcpy(&buffer_[0], data.data() + first_part, (to_write - first_part) * sizeof(int16_t));
    }

    write_index_.store(current_write + to_write, std::memory_order_release);
    return to_write;
}

size_t AudioRingBuffer::pop(std::span<int16_t> output) noexcept {
    const size_t read_avail = available_read();
    const size_t to_read = std::min(output.size(), read_avail);
    if (to_read == 0) return 0;

    const size_t current_read = read_index_.load(std::memory_order_relaxed);
    const size_t index = current_read & (TOTAL_SAMPLES - 1);
    const size_t first_part = std::min(to_read, TOTAL_SAMPLES - index);

    std::memcpy(output.data(), &buffer_[index], first_part * sizeof(int16_t));
    if (to_read > first_part) {
        std::memcpy(output.data() + first_part, &buffer_[0], (to_read - first_part) * sizeof(int16_t));
    }

    read_index_.store(current_read + to_read, std::memory_order_release);
    return to_read;
}

void AudioRingBuffer::clear_and_wipe() noexcept {
    write_index_.store(0, std::memory_order_relaxed);
    read_index_.store(0, std::memory_order_relaxed);
    shruti::memory::wipe_pcm_frame(buffer_, TOTAL_SAMPLES);
}

} // namespace shruti::audio
