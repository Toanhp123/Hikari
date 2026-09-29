#ifndef HIKARI_LNREADER_RUNTIME_H
#define HIKARI_LNREADER_RUNTIME_H
/* Host owns returned UTF-8 JSON buffer; NULL means bounded execution failed.
 * Callback owns its malloc-allocated return. No OS/module loader is installed. */
typedef char *(*HikariHostCall)(void *opaque, const char *request);
char *hikari_js(const char *script, int timeout_ms, HikariHostCall host, void *opaque);
#endif
