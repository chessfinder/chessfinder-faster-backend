#ifndef __CHESS_FINDER_CORE_H
#define __CHESS_FINDER_CORE_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef int (*validate_fn_t)(graal_isolatethread_t* thread, char* searchFenCString);

typedef int (*find_fn_t)(graal_isolatethread_t* thread, char* searchFenCString, char* gamePgnCString);

#if defined(__cplusplus)
}
#endif
#endif
