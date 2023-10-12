package main

/*
#cgo LDFLAGS: ./chess-finder-core.so
#include <stdlib.h>
#include <stdio.h>
#include "chess-finder-core.h"
*/
import "C"

import (
	"fmt"
)

func main() {

	i := 10000

	for i > 0 {
		i--
		var isolate *C.graal_isolate_t = nil
		var thread *C.graal_isolatethread_t = nil

		if C.graal_create_isolate(nil, &isolate, &thread) != 0 {
			fmt.Println("Initialization error")
			return
		}

		result := C.validate(thread, C.CString("????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"))
		fmt.Printf("validate: %v\n", result)

		found := C.find(thread, C.CString("????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"), C.CString("[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.11.24\"]\n[Round \"-\"]\n[White \"tigran-c-137\"]\n[Black \"philimon93\"]\n[Result \"1-0\"]\n[CurrentPosition \"4R1r1/1p3kq1/p3Q3/3p1p2/BP6/P7/6PP/7K b - -\"]\n[Timezone \"UTC\"]\n[ECO \"B06\"]\n[ECOUrl \"https://www.chess.com/openings/Modern-Defense-Three-Pawns-Attack-with-2-f4\"]\n[UTCDate \"2022.11.24\"]\n[UTCTime \"10:44:47\"]\n[WhiteElo \"1533\"]\n[BlackElo \"1427\"]\n[TimeControl \"600\"]\n[Termination \"tigran-c-137 won by checkmate\"]\n[StartTime \"10:44:47\"]\n[EndDate \"2022.11.24\"]\n[EndTime \"11:04:16\"]\n[Link \"https://www.chess.com/game/live/63025767719\"]\n\n1. e4 {[%clk 0:09:57.7]} 1... g6 {[%clk 0:09:57.3]} 2. f4 {[%clk 0:09:52.6]} 2... e6 {[%clk 0:09:49.4]} 3. Nf3 {[%clk 0:09:50.5]} 3... Nc6 {[%clk 0:09:47.1]} 4. Be2 {[%clk 0:09:33.3]} 4... Nge7 {[%clk 0:09:43.3]} 5. O-O {[%clk 0:09:30.2]} 5... d5 {[%clk 0:09:41]} 6. exd5 {[%clk 0:08:58.4]} 6... Nxd5 {[%clk 0:09:39.5]} 7. d3 {[%clk 0:08:50.1]} 7... Bd6 {[%clk 0:09:30]} 8. f5 {[%clk 0:08:08.2]} 8... exf5 {[%clk 0:09:20.6]} 9. Bh6 {[%clk 0:08:07.8]} 9... Be6 {[%clk 0:09:01.4]} 10. c4 {[%clk 0:07:47.6]} 10... Nde7 {[%clk 0:08:39]} 11. d4 {[%clk 0:07:16.9]} 11... Bd7 {[%clk 0:08:10]} 12. d5 {[%clk 0:07:00.8]} 12... Bc5+ {[%clk 0:08:08.2]} 13. Kh1 {[%clk 0:06:59.2]} 13... Na5 {[%clk 0:07:52]} 14. a3 {[%clk 0:06:49.6]} 14... Bb6 {[%clk 0:07:07.9]} 15. b4 {[%clk 0:06:21.7]} 15... Nxc4 {[%clk 0:06:50.4]} 16. Bxc4 {[%clk 0:06:19.1]} 16... c6 {[%clk 0:06:49.4]} 17. d6 {[%clk 0:06:04.1]} 17... Nd5 {[%clk 0:06:06.5]} 18. Qe1+ {[%clk 0:05:01.1]} 18... Be6 {[%clk 0:06:03.8]} 19. Nc3 {[%clk 0:04:55]} 19... Qxd6 {[%clk 0:05:41.5]} 20. Nxd5 {[%clk 0:04:38.6]} 20... cxd5 {[%clk 0:05:39.5]} 21. Bb5+ {[%clk 0:04:21.9]} 21... Ke7 {[%clk 0:05:15.1]} 22. Bg7 {[%clk 0:03:31.6]} 22... Rhf8 {[%clk 0:04:20.9]} 23. Qh4+ {[%clk 0:03:15.5]} 23... f6 {[%clk 0:04:19.7]} 24. Bxf8+ {[%clk 0:03:14.9]} 24... Rxf8 {[%clk 0:04:17.5]} 25. Qxh7+ {[%clk 0:03:13]} 25... Rf7 {[%clk 0:04:16.6]} 26. Qg8 {[%clk 0:02:36.6]} 26... Rf8 {[%clk 0:03:43.5]} 27. Qxg6 {[%clk 0:02:35.7]} 27... Bc7 {[%clk 0:03:36.9]} 28. Rae1 {[%clk 0:02:09.5]} 28... a6 {[%clk 0:03:21.1]} 29. Ba4 {[%clk 0:01:58.3]} 29... Rg8 {[%clk 0:03:07.1]} 30. Qh7+ {[%clk 0:01:32.5]} 30... Kf8 {[%clk 0:02:50.8]} 31. Qh6+ {[%clk 0:00:58.7]} 31... Kf7 {[%clk 0:02:38.5]} 32. Qh5+ {[%clk 0:00:18.8]} 32... Ke7 {[%clk 0:01:45]} 33. Rxe6+ {[%clk 0:00:17.4]} 33... Qxe6 {[%clk 0:01:28.6]} 34. Re1 {[%clk 0:00:16.7]} 34... Be5 {[%clk 0:01:03.4]} 35. Nxe5 {[%clk 0:00:15.5]} 35... fxe5 {[%clk 0:00:58.8]} 36. Qh7+ {[%clk 0:00:14.4]} 36... Qf7 {[%clk 0:00:52.7]} 37. Rxe5+ {[%clk 0:00:13.1]} 37... Kf8 {[%clk 0:00:52.4]} 38. Qh6+ {[%clk 0:00:09.6]} 38... Qg7 {[%clk 0:00:45.8]} 39. Re8+ {[%clk 0:00:07.3]} 39... Kf7 {[%clk 0:00:45.4]} 40. Qe6# {[%clk 0:00:06.2]} 1-0\n"))
		fmt.Printf("found: %v\n", found)

		notFound := C.find(thread, C.CString("????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????"), C.CString("[Event \"Live Chess\"]\n[Site \"Chess.com\"]\n[Date \"2022.11.24\"]\n[Round \"-\"]\n[White \"dedesupriadi69\"]\n[Black \"tigran-c-137\"]\n[Result \"1-0\"]\n[CurrentPosition \"2k4r/P1pR4/1pP1p3/3nqp1p/Q7/P5Pp/5P1K/8 b - -\"]\n[Timezone \"UTC\"]\n[ECO \"B01\"]\n[ECOUrl \"https://www.chess.com/openings/Scandinavian-Defense-Mieses-Kotrc-Main-Line-4.Nf3\"]\n[UTCDate \"2022.11.24\"]\n[UTCTime \"09:55:17\"]\n[WhiteElo \"1617\"]\n[BlackElo \"1527\"]\n[TimeControl \"600\"]\n[Termination \"dedesupriadi69 won on time\"]\n[StartTime \"09:55:17\"]\n[EndDate \"2022.11.24\"]\n[EndTime \"10:08:25\"]\n[Link \"https://www.chess.com/game/live/63022768329\"]\n\n1. e4 {[%clk 0:09:54.2]} 1... d5 {[%clk 0:09:59.1]} 2. exd5 {[%clk 0:09:52.1]} 2... Qxd5 {[%clk 0:09:58.7]} 3. Nc3 {[%clk 0:09:50.8]} 3... Qa5 {[%clk 0:09:57.8]} 4. Nf3 {[%clk 0:09:46.2]} 4... Bg4 {[%clk 0:09:56.8]} 5. Be2 {[%clk 0:09:44.4]} 5... Nc6 {[%clk 0:09:55.5]} 6. O-O {[%clk 0:09:43.1]} 6... O-O-O {[%clk 0:09:54.1]} 7. a3 {[%clk 0:09:40.5]} 7... h5 {[%clk 0:09:46.6]} 8. b4 {[%clk 0:09:39]} 8... Qf5 {[%clk 0:09:31.2]} 9. Bd3 {[%clk 0:09:33.3]} 9... Qe6 {[%clk 0:08:23.8]} 10. Re1 {[%clk 0:09:31.4]} 10... Bxf3 {[%clk 0:08:10.8]} 11. Qxf3 {[%clk 0:09:27.9]} 11... Qxe1+ {[%clk 0:08:09.3]} 12. Bf1 {[%clk 0:09:24.6]} 12... e6 {[%clk 0:06:38.1]} 13. Bb2 {[%clk 0:09:22.8]} 13... Qxd2 {[%clk 0:06:36.9]} 14. Rd1 {[%clk 0:09:18.5]} 14... Qg5 {[%clk 0:06:31]} 15. Bd3 {[%clk 0:09:11.8]} 15... Bd6 {[%clk 0:06:12.9]} 16. Nb5 {[%clk 0:09:06.5]} 16... Ne5 {[%clk 0:05:27.6]} 17. Nxd6+ {[%clk 0:09:03.4]} 17... Rxd6 {[%clk 0:05:01]} 18. Bxe5 {[%clk 0:09:01.7]} 18... Rxd3 {[%clk 0:04:43.7]} 19. Rxd3 {[%clk 0:08:55.4]} 19... Qxe5 {[%clk 0:04:42.9]} 20. h3 {[%clk 0:08:49.1]} 20... f5 {[%clk 0:04:19.4]} 21. Qd1 {[%clk 0:08:41]} 21... Ne7 {[%clk 0:04:10.4]} 22. Rd7 {[%clk 0:08:37.4]} 22... Qf6 {[%clk 0:03:41.3]} 23. b5 {[%clk 0:08:30.3]} 23... Nd5 {[%clk 0:03:27.3]} 24. c4 {[%clk 0:08:07.9]} 24... Nc3 {[%clk 0:03:03.7]} 25. Qd3 {[%clk 0:07:59.1]} 25... g5 {[%clk 0:02:57.5]} 26. c5 {[%clk 0:07:46.2]} 26... Nd5 {[%clk 0:02:37.5]} 27. c6 {[%clk 0:07:42.1]} 27... b6 {[%clk 0:02:33.9]} 28. Qc4 {[%clk 0:07:32.2]} 28... g4 {[%clk 0:02:23.4]} 29. Qa4 {[%clk 0:07:30.3]} 29... a5 {[%clk 0:00:46.7]} 30. bxa6 {[%clk 0:07:24.1]} 30... gxh3 {[%clk 0:00:31.6]} 31. a7 {[%clk 0:07:17.5]} 31... Qa1+ {[%clk 0:00:17.8]} 32. Kh2 {[%clk 0:07:12.3]} 32... Qe5+ {[%clk 0:00:12.2]} 33. g3 {[%clk 0:07:08.4]} 1-0\n"))
		fmt.Printf("notFound: %v\n", notFound)

		C.graal_tear_down_isolate(thread)
	}
}
