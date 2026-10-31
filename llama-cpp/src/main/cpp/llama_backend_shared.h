#pragma once

#include <mutex>
#include "llama.h"

// The process owns the backend. A context owner must never tear it down under another model.
inline void ensureLlamaBackend() {
    static std::once_flag initialized;
    std::call_once(initialized, [] { llama_backend_init(); });
}
