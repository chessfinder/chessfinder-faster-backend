package batcher

import (
	"reflect"
	"testing"
)

func TestBatcher(t *testing.T) {
	tests := []struct {
		name      string
		items     []int
		batchSize int
		want      [][]int
	}{
		{
			name:      "standard batch",
			items:     []int{1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
			batchSize: 3,
			want:      [][]int{{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {10}},
		},
		{
			name:      "exact multiple",
			items:     []int{1, 2, 3, 4, 5, 6},
			batchSize: 3,
			want:      [][]int{{1, 2, 3}, {4, 5, 6}},
		},
		{
			name:      "empty slice",
			items:     []int{},
			batchSize: 3,
			want:      [][]int{},
		},
		{
			name:      "batch size larger than slice",
			items:     []int{1, 2},
			batchSize: 5,
			want:      [][]int{{1, 2}},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := Batcher(tt.items, tt.batchSize)
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Batcher() = %v, want %v", got, tt.want)
			}
		})
	}
}
