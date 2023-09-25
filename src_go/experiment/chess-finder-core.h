#ifndef __CHESS_FINDER_CORE_H
#define __CHESS_FINDER_CORE_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int validate(graal_isolatethread_t* thread, char* searchFenCString);

int find(graal_isolatethread_t* thread, char* searchFenCString, char* gamePgnCString);

#if defined(__cplusplus)
}
#endif
#endif
