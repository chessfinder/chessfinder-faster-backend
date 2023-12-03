package searcher

/*
#cgo LDFLAGS: ./searcher/chess-finder-core.so
#include <stdlib.h>
#include <stdio.h>
#include "chess-finder-core.h"
*/
import "C"

import (
	"fmt"
	"runtime/debug"
	"unsafe"
)

type BoardSearcher interface {
	SearchBoard(board string, pgn string) (bool, error)
}

type DefaultBoardSearcher struct{}

func (searcher DefaultBoardSearcher) SearchBoard(board string, pgn string) (bool, error) {
	debug.SetPanicOnFault(true)
	var err error

	defer func() {
		if r := recover(); r != nil {
			fmt.Println("Recovered in SearchBoard", r)
			err = fmt.Errorf("%v", r)
		}
	}()

	var isolate *C.graal_isolate_t = nil
	var thread *C.graal_isolatethread_t = nil

	if C.graal_create_isolate(nil, &isolate, &thread) != 0 {
		fmt.Println("Initialization error")
		return false, err
	}

	defer C.graal_tear_down_isolate(thread)
	cBoard := C.CString(board)
	defer C.free(unsafe.Pointer(cBoard))
	cPgn := C.CString(pgn)
	defer C.free(unsafe.Pointer(cPgn))
	isFound := C.find(thread, cBoard, cPgn)

	return isFound != 0, err
}
