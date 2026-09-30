#include "runtime.h"
#include "quickjs.h"
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define MAX_BYTES (4 * 1024 * 1024)
typedef struct { int64_t deadline; HikariHostCall host; void *opaque; } Execution;
static int64_t now_ms(void) {
  struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
  return (int64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}
static int interrupted(JSRuntime *rt, void *opaque) {
  (void)rt; return now_ms() >= ((Execution *)opaque)->deadline;
}
static JSValue host_call(JSContext *ctx, JSValueConst self, int argc, JSValueConst *argv) {
  (void)self;
  Execution *e = JS_GetContextOpaque(ctx);
  if (!e->host || argc != 1 || now_ms() >= e->deadline)
    return JS_ThrowInternalError(ctx, "host unavailable");
  size_t size; const char *request = JS_ToCStringLen(ctx, &size, argv[0]);
  if (!request) return JS_EXCEPTION;
  if (size > MAX_BYTES) { JS_FreeCString(ctx, request); return JS_ThrowRangeError(ctx, "request limit"); }
  char *response = e->host(e->opaque, request);
  JS_FreeCString(ctx, request);
  if (!response) return JS_ThrowInternalError(ctx, "host rejected request");
  JSValue value = strlen(response) <= MAX_BYTES && now_ms() < e->deadline
    ? JS_NewString(ctx, response) : JS_ThrowRangeError(ctx, "host response limit");
  free(response); return value;
}
char *hikari_js(const char *script, int timeout_ms, HikariHostCall host, void *opaque) {
  if (!script || strlen(script) > MAX_BYTES || timeout_ms < 1 || timeout_ms > 10000) return NULL;
  Execution e = {now_ms() + timeout_ms, host, opaque};
  JSRuntime *rt = JS_NewRuntime(); if (!rt) return NULL;
  JS_SetMemoryLimit(rt, 32 * 1024 * 1024);
  JS_SetMaxStackSize(rt, 512 * 1024);
  JS_SetInterruptHandler(rt, interrupted, &e);
  JSContext *ctx = JS_NewContext(rt); char *output = NULL;
  if (!ctx) { JS_FreeRuntime(rt); return NULL; }
  JS_SetContextOpaque(ctx, &e);
  JSValue global = JS_GetGlobalObject(ctx);
  JS_SetPropertyStr(ctx, global, "__hikariHost", JS_NewCFunction(ctx, host_call, "host", 1));
  JS_FreeValue(ctx, global);
  JSValue result = JS_Eval(ctx, script, strlen(script), "lnreader.js", JS_EVAL_TYPE_GLOBAL);
  if (!JS_IsException(result)) {
    while (JS_PromiseState(ctx, result) == JS_PROMISE_PENDING && now_ms() < e.deadline) {
      JSContext *job_ctx; int jobs = JS_ExecutePendingJob(rt, &job_ctx);
      if (jobs <= 0) break;
    }
    int state = JS_PromiseState(ctx, result);
    if (state == JS_PROMISE_FULFILLED) {
      JSValue resolved = JS_PromiseResult(ctx, result); JS_FreeValue(ctx, result); result = resolved;
    }
    if ((state == -1 || state == JS_PROMISE_FULFILLED) && now_ms() < e.deadline) {
      JSValue json = JS_JSONStringify(ctx, result, JS_UNDEFINED, JS_UNDEFINED);
      if (!JS_IsException(json) && !JS_IsUndefined(json)) {
        size_t size; const char *text = JS_ToCStringLen(ctx, &size, json);
        if (text && size <= MAX_BYTES) { output = malloc(size + 1); if (output) memcpy(output, text, size + 1); }
        if (text) JS_FreeCString(ctx, text);
      }
      JS_FreeValue(ctx, json);
    }
  }
  JS_FreeValue(ctx, result); JS_FreeContext(ctx); JS_FreeRuntime(rt); return output;
}
