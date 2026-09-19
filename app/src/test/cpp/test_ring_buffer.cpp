#include <iostream>
#include <vector>
#include <cassert>
#include "../../main/cpp/include/shruti_ring_buffer.hpp"
#include "../../main/cpp/include/shruti_memory_sanitizer.hpp"

int main() {
    shruti::audio::AudioRingBuffer ringBuffer;

    // Test 1: Available write initial capacity
    assert(ringBuffer.available_write() == 32768);
    assert(ringBuffer.available_read() == 0);

    // Test 2: Push frames
    std::vector<int16_t> inputData(320, 1234); // 20ms frame
    size_t pushed = ringBuffer.push(inputData);
    assert(pushed == 320);
    assert(ringBuffer.available_read() == 320);

    // Test 3: Pop frames
    std::vector<int16_t> outputData(320, 0);
    size_t popped = ringBuffer.pop(outputData);
    assert(popped == 320);
    assert(outputData[0] == 1234);
    assert(ringBuffer.available_read() == 0);

    // Test 4: Memory wiping
    ringBuffer.push(inputData);
    ringBuffer.clear_and_wipe();
    assert(ringBuffer.available_read() == 0);

    std::cout << "[SUCCESS] RingBuffer Unit Tests Passed!" << std::endl;
    return 0;
}
