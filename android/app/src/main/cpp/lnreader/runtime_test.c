#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "runtime.h"

static void check(int condition, const char *message) {
  if (!condition) {
    fprintf(stderr, "runtime test failed: %s\n", message);
    exit(EXIT_FAILURE);
  }
}

static char *fixture_host(void *opaque, const char *request) {
  (void)opaque;
  if (strstr(request, "\"op\":\"fetch\"")) return strdup("{\"status\":200,\"body\":\"<h1>Novel</h1>\"}");
  if (strstr(request, "\"op\":\"get\"")) return strdup("\"saved\"");
  if (strstr(request, "\"op\":\"absoluteUrl\"")) return strdup("\"https://example.org/chapter\"");
  return strdup("null");
}

int main(int argc, char **argv) {
  check(argc == 1 || argc == 3, "expected zero or two fixture paths");
  if (argc == 3) {
    char *parts[2];
    size_t sizes[2];
    for (int i = 0; i < 2; i++) {
      FILE *file = fopen(argv[i + 1], "rb");
      check(file != NULL, "open fixture");
      check(fseek(file, 0, SEEK_END) == 0, "seek fixture end");
      long length = ftell(file);
      check(length >= 0 && (uintmax_t)length <= SIZE_MAX - 1, "fixture size");
      sizes[i] = (size_t)length;
      check(fseek(file, 0, SEEK_SET) == 0, "rewind fixture");
      parts[i] = calloc(sizes[i] + 1, 1);
      check(parts[i] != NULL, "allocate fixture");
      check(fread(parts[i], 1, sizes[i], file) == sizes[i] && !ferror(file), "read fixture");
      check(fclose(file) == 0, "close fixture");
    }
    check(sizes[1] <= SIZE_MAX - 2 && sizes[0] <= SIZE_MAX - sizes[1] - 2, "combined fixture size");
    char *script = calloc(sizes[0] + sizes[1] + 2, 1);
    check(script != NULL, "allocate combined fixture");
    memcpy(script, parts[0], sizes[0]);
    script[sizes[0]] = '\n';
    memcpy(script + sizes[0] + 1, parts[1], sizes[1]);
    char *value = hikari_js(script, 5000, fixture_host, NULL);
    check(value && strcmp(value, "true") == 0, "bundled host contract");
    free(value); free(script); free(parts[0]); free(parts[1]);
    puts("real bundled cheerio/dayjs/fetch/storage/absoluteUrl contract PASS");
  }
  char *result = hikari_js("Promise.resolve({ok:42})", 100, NULL, NULL);
  check(result && strcmp(result, "{\"ok\":42}") == 0, "resolved promise"); free(result);
  check(hikari_js("while(true){}", 50, NULL, NULL) == NULL, "deadline");
  check(hikari_js("let a=[]; while(true) a.push(new Array(10000).fill('x'))", 200, NULL, NULL) == NULL, "heap limit");
  check(hikari_js("function f(){return f()+1}; f()", 100, NULL, NULL) == NULL, "stack limit");
  check(hikari_js("new Promise(()=>{})", 50, NULL, NULL) == NULL, "pending promise");
  result = hikari_js("Promise.resolve(typeof std + ':' + typeof os + ':' + typeof require)", 100, NULL, NULL);
  check(result && strcmp(result, "\"undefined:undefined:undefined\"") == 0, "no ambient modules"); free(result);
  result = hikari_js("Promise.resolve('Tiếng Việt 😀\\u0000end')", 100, NULL, NULL);
  check(result && strstr(result, "Tiếng Việt 😀") && strstr(result, "\\u0000end"), "Unicode and NUL"); free(result);
  check(hikari_js("Promise.resolve({toJSON(){throw Error('bad')}})", 100, NULL, NULL) == NULL, "serialization rejection");
  check(hikari_js("Promise.reject(Error('bad'))", 100, NULL, NULL) == NULL, "rejected promise");
  puts("bounded QuickJS: promise, deadline, memory, stack, pending promise, no ambient modules, Unicode, serialization rejection PASS");
  return EXIT_SUCCESS;
}
