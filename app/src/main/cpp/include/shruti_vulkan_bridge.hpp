#ifndef SHRUTI_VULKAN_BRIDGE_HPP
#define SHRUTI_VULKAN_BRIDGE_HPP

#include <cstddef>
#include <cstdint>
#include <vector>
#include <string>
#include <mutex>
#include <memory>
#include <vulkan/vulkan.h>

namespace shruti::compute {

/**
 * Constants for S.H.R.U.T.I. Vulkan & Vectorizer Engine
 */
constexpr size_t SPEAKER_EMBEDDING_DIM = 192;   // Speaker identification d-vector
constexpr size_t PROSODY_EMBEDDING_DIM = 64;    // Cadence & inflection embedding
constexpr size_t INTENT_EMBEDDING_DIM  = 512;   // Session intent summary vector
constexpr size_t PCM_SAMPLE_RATE       = 16000; // 16 kHz linear PCM
constexpr size_t SAMPLES_PER_CHUNK     = 8000;  // 500 ms acoustic chunk (8,000 samples)

/**
 * Execution silicon backend types for llama.cpp / GGML.
 */
enum class ComputeBackend {
    VULKAN_GPU,
    ARM_NEON_CPU
};

/**
 * Universal Vulkan Compute & llama.cpp Acceleration Bridge.
 * Binds directly to Android Khronos Vulkan 1.1+ (libvulkan.so) across Adreno, Mali, Xclipse,
 * with multithreaded ARM NEON CPU fallback.
 */
class VulkanComputeEngine {
public:
    static VulkanComputeEngine& instance();

    VulkanComputeEngine();
    ~VulkanComputeEngine();

    // Non-copyable, non-movable
    VulkanComputeEngine(const VulkanComputeEngine&) = delete;
    VulkanComputeEngine& operator=(const VulkanComputeEngine&) = delete;

    /**
     * Checks if Vulkan 1.1+ is supported on the current Android physical device.
     */
    [[nodiscard]] bool check_vulkan_support() noexcept;

    /**
     * Initializes llama.cpp / GGML backend with Vulkan GPU offload (-DGGML_VULKAN=ON).
     * Loads the GGUF model container via mmap.
     */
    bool initialize(const std::string& model_path);

    /**
     * Returns the active compute backend in use.
     */
    [[nodiscard]] ComputeBackend get_active_backend() const noexcept;

    /**
     * Extracts dual embeddings (192-d speaker + 64-d prosody) from 16kHz PCM audio chunk.
     * Evaluates via Vulkan compute shaders in <= 16 ms per 500 ms frame.
     */
    bool extract_vectors(
        const int16_t* pcm_data,
        size_t sample_count,
        float* out_speaker_vec,
        float* out_prosody_vec
    ) noexcept;

    /**
     * Synthesizes expressive spoken debrief audio from decrypted trajectory matrix.
     * Runs Qwen3-Omni backbone + SNAC heads on Vulkan GPU in <= 25 ms/token.
     * Outputs 16 kHz PCM audio frames to out_pcm buffer.
     */
    int synthesize_debrief(
        const float* trajectory_matrix,
        size_t vector_count,
        int16_t* out_pcm,
        size_t max_samples
    ) noexcept;

    /**
     * Binds a 512-d speaker conditioning vector preset (Aditi, Agastya, Priya, Kabir)
     * to the SNAC decoder head.
     */
    bool set_speaker_preset(const float* preset_data, size_t dim) noexcept;

    /**
     * Securely scrubs all model activations, KV-cache, and intermediate tensors from RAM.
     */
    void wipe_memory() noexcept;

    /**
     * Tears down Vulkan resources and cleans up runtime context.
     */
    void teardown() noexcept;

private:
    bool init_vulkan_instance() noexcept;

    std::mutex engine_mutex_;
    VkInstance vk_instance_{VK_NULL_HANDLE};
    VkPhysicalDevice vk_physical_device_{VK_NULL_HANDLE};
    VkDevice vk_device_{VK_NULL_HANDLE};
    ComputeBackend active_backend_{ComputeBackend::ARM_NEON_CPU};

    bool initialized_{false};
    bool vulkan_supported_{false};
    std::string loaded_model_path_;

    // Preserved 512-d speaker conditioning preset vector
    alignas(64) float current_speaker_preset_[INTENT_EMBEDDING_DIM]{};
    bool preset_configured_{false};
};

} // namespace shruti::compute

#endif // SHRUTI_VULKAN_BRIDGE_HPP
