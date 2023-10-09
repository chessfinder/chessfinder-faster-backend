package batcher

func Batcher[T any](items []T, batchSize int) [][]T {
	numBatches := len(items) / batchSize
	if len(items)%batchSize != 0 {
		numBatches++
	}

	batches := make([][]T, 0, numBatches)

	for i := 0; i < len(items); i += batchSize {
		end := i + batchSize
		if end > len(items) {
			end = len(items)
		}
		batches = append(batches, items[i:end])
	}

	return batches
}
