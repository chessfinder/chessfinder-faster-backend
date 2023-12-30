package logging

import (
	"time"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

func MustCreateZuluTimeLogger() (logger *zap.Logger) {

	config := zap.NewProductionConfig()
	config.OutputPaths = []string{"stdout"}
	timeEncoder := func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.UTC().Format("2006-01-02T15:04:05.000Z"))
	}

	config.EncoderConfig.EncodeTime = timeEncoder

	logger, err := config.Build()
	if err != nil {
		panic(err)
	}
	return logger
}
