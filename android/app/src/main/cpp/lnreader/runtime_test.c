#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "runtime.h"

static char *fixture_host(void *opaque, const char *request) {
  (void)opaque;
  if (strstr(request, "\"op\":\"fetch\"")) return strdup("{\"status\":200,\"body\":\"<h1>Novel</h1>\"}");
  if (strstr(request, "\"op\":\"get\"")) return strdup("\"saved\"");
  if (strstr(request, "\"op\":\"absoluteUrl\"")) return strdup("\"https://example.org/chapter\"");
  return strdup("null");
}
int main(int argc, char **argv) {
  if (argc == 3) {
    char *parts[2]; size_t sizes[2];
    for(int i=0;i<2;i++) { FILE *f=fopen(argv[i+1],"rb"); assert(f); fseek(f,0,SEEK_END); sizes[i]=ftell(f); rewind(f); parts[i]=calloc(sizes[i]+1,1); assert(fread(parts[i],1,sizes[i],f)==sizes[i]); fclose(f); }
    char *script=calloc(sizes[0]+sizes[1]+2,1); memcpy(script,parts[0],sizes[0]); script[sizes[0]]='\n'; memcpy(script+sizes[0]+1,parts[1],sizes[1]);
    char *value=hikari_js(script,5000,fixture_host,NULL); assert(value && strcmp(value,"true")==0);
    free(value); free(script); free(parts[0]); free(parts[1]); puts("real bundled cheerio/dayjs/fetch/storage/absoluteUrl contract PASS");
  }
  char *result = hikari_js("Promise.resolve({ok:42})", 100, NULL, NULL);
  assert(result && strcmp(result, "{\"ok\":42}") == 0); free(result);
  assert(hikari_js("while(true){}", 50, NULL, NULL) == NULL);
  assert(hikari_js("let a=[]; while(true) a.push(new Array(10000).fill('x'))", 200, NULL, NULL) == NULL);
  assert(hikari_js("function f(){return f()+1}; f()", 100, NULL, NULL) == NULL);
  assert(hikari_js("new Promise(()=>{})", 50, NULL, NULL) == NULL);
  result = hikari_js("Promise.resolve(typeof std + ':' + typeof os + ':' + typeof require)", 100, NULL, NULL);
  assert(result && strcmp(result, "\"undefined:undefined:undefined\"") == 0); free(result);
  result = hikari_js("Promise.resolve('Tiếng Việt 😀\\u0000end')", 100, NULL, NULL);
  assert(result && strstr(result, "Tiếng Việt 😀") && strstr(result, "\\u0000end")); free(result);
  assert(hikari_js("Promise.resolve({toJSON(){throw Error('bad')}})", 100, NULL, NULL) == NULL);
  assert(hikari_js("Promise.reject(Error('bad'))", 100, NULL, NULL) == NULL);
  puts("bounded QuickJS: promise, deadline, memory, stack, pending promise, no ambient modules, Unicode, serialization rejection PASS");
}
