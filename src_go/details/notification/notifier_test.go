package main

import (
	"fmt"
	"net/http"
	"testing"

	"github.com/aws/aws-lambda-go/events"
	"github.com/stretchr/testify/assert"
	"github.com/wiremock/go-wiremock"
)

var notifier = Notifier{
	telegramUrl:             "http://0.0.0.0:18443",
	telegramBotApiKey:       "theBotKey",
	telegramChatId:          int64(123),
	telegramInsightsTopicId: int64(456),
	telegramInsightsAlarms:  []string{},
	telegramHealthTopicId:   int64(789),
	telegramHealthAlarms:    []string{},
}

var wiremockClient = wiremock.NewClient("http://0.0.0.0:18443")

func Test_when_notifier_gets_ALARM_insight_sends_it_to_the_insights_topic_as_attention_needed(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	insightAlarmName := "insight1"
	alarmDescription := "very important alarm"
	notifier.telegramInsightsAlarms = []string{insightAlarmName}

	messageText := fmt.Sprintf(`🤬💩🍆 %s\n%s`,
		insightAlarmName,
		alarmDescription,
	)

	message := fmt.Sprintf(`{"chat_id":123,"text":"%s", "message_thread_id":456}`, messageText)

	stubNotification := wiremock.Post(wiremock.URLPathEqualTo(fmt.Sprintf("/bot%s/sendMessage", notifier.telegramBotApiKey))).
		WithBodyPattern(wiremock.EqualToJson(message)).
		WithHeader("Accept", wiremock.EqualTo("application/json")).
		WillReturnResponse(
			wiremock.NewResponse().
				WithStatus(http.StatusOK).
				WithHeader("Content-Type", "application/json").
				WithBody(`{"ok": true}`),
		)

	err = wiremockClient.StubFor(stubNotification)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"AlarmName": "%s",
					"AlarmDescription": "%s",
					"NewStateValue": "ALARM",
					"NewStateReason": "Threshold Crossed: no datapoints were received for 3 periods and 3 missing datapoints were treated as [NonBreaching].",
					"StateChangeTime": "2023-12-30T07:48:51.322+0000"
				}
			`,
				insightAlarmName,
				alarmDescription,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := notifier.Notify(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	verifyDownloadedCall, err := wiremockClient.Verify(stubNotification.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_notifier_gets_OK_insight_sends_it_to_the_insights_topic_as_calming(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	insightAlarmName := "insight1"
	alarmDescription := "very important alarm"
	notifier.telegramInsightsAlarms = []string{insightAlarmName}

	messageText := fmt.Sprintf(`🏆🍾💰 %s\n%s`,
		insightAlarmName,
		alarmDescription,
	)

	message := fmt.Sprintf(`{"chat_id":123,"text":"%s", "message_thread_id":456}`, messageText)

	stubNotification := wiremock.Post(wiremock.URLPathEqualTo(fmt.Sprintf("/bot%s/sendMessage", notifier.telegramBotApiKey))).
		WithBodyPattern(wiremock.EqualToJson(message)).
		WithHeader("Accept", wiremock.EqualTo("application/json")).
		WillReturnResponse(
			wiremock.NewResponse().
				WithStatus(http.StatusOK).
				WithHeader("Content-Type", "application/json").
				WithBody(`{"ok": true}`),
		)

	err = wiremockClient.StubFor(stubNotification)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"AlarmName": "%s",
					"AlarmDescription": "%s",
					"NewStateValue": "OK",
					"NewStateReason": "Threshold Crossed: no datapoints were received for 3 periods and 3 missing datapoints were treated as [NonBreaching].",
					"StateChangeTime": "2023-12-30T07:48:51.322+0000"
				}
			`,
				insightAlarmName,
				alarmDescription,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := notifier.Notify(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	verifyDownloadedCall, err := wiremockClient.Verify(stubNotification.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_notifier_gets_ALARM_health_notification_sends_it_to_the_health_topic_as_attention_needed(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	healthAlarmName := "health1"
	alarmDescription := "very important alarm"
	notifier.telegramHealthAlarms = []string{healthAlarmName}

	messageText := fmt.Sprintf(`🧨☠️🚨 %s\n%s`,
		healthAlarmName,
		alarmDescription,
	)

	message := fmt.Sprintf(`{"chat_id":123,"text":"%s", "message_thread_id":789}`, messageText)

	stubNotification := wiremock.Post(wiremock.URLPathEqualTo(fmt.Sprintf("/bot%s/sendMessage", notifier.telegramBotApiKey))).
		WithBodyPattern(wiremock.EqualToJson(message)).
		WithHeader("Accept", wiremock.EqualTo("application/json")).
		WillReturnResponse(
			wiremock.NewResponse().
				WithStatus(http.StatusOK).
				WithHeader("Content-Type", "application/json").
				WithBody(`{"ok": true}`),
		)

	err = wiremockClient.StubFor(stubNotification)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"AlarmName": "%s",
					"AlarmDescription": "%s",
					"NewStateValue": "ALARM",
					"NewStateReason": "Threshold Crossed: no datapoints were received for 3 periods and 3 missing datapoints were treated as [NonBreaching].",
					"StateChangeTime": "2023-12-30T07:48:51.322+0000"
				}
			`,
				healthAlarmName,
				alarmDescription,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := notifier.Notify(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	verifyDownloadedCall, err := wiremockClient.Verify(stubNotification.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_notifier_gets_OK_health_notification_sends_it_to_the_insights_topic_as_calming(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	healthAlarmName := "health1"
	alarmDescription := "very important alarm"
	notifier.telegramHealthAlarms = []string{healthAlarmName}

	messageText := fmt.Sprintf(`💚☕️🏖 %s\n%s`,
		healthAlarmName,
		alarmDescription,
	)

	message := fmt.Sprintf(`{"chat_id":123,"text":"%s", "message_thread_id":789}`, messageText)

	stubNotification := wiremock.Post(wiremock.URLPathEqualTo(fmt.Sprintf("/bot%s/sendMessage", notifier.telegramBotApiKey))).
		WithBodyPattern(wiremock.EqualToJson(message)).
		WithHeader("Accept", wiremock.EqualTo("application/json")).
		WillReturnResponse(
			wiremock.NewResponse().
				WithStatus(http.StatusOK).
				WithHeader("Content-Type", "application/json").
				WithBody(`{"ok": true}`),
		)

	err = wiremockClient.StubFor(stubNotification)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"AlarmName": "%s",
					"AlarmDescription": "%s",
					"NewStateValue": "OK",
					"NewStateReason": "Threshold Crossed: no datapoints were received for 3 periods and 3 missing datapoints were treated as [NonBreaching].",
					"StateChangeTime": "2023-12-30T07:48:51.322+0000"
				}
			`,
				healthAlarmName,
				alarmDescription,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := notifier.Notify(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)
	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	verifyDownloadedCall, err := wiremockClient.Verify(stubNotification.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}

func Test_when_notifier_get_OK_for_unknown_notification_sends_it_to_the_general_chat(t *testing.T) {
	var err error
	defer wiremockClient.Reset()

	healthAlarmName := "unknown"
	alarmDescription := "very important alarm"

	messageText := fmt.Sprintf(`🚨☎️📢 %s\n%s`,
		healthAlarmName,
		alarmDescription,
	)

	message := fmt.Sprintf(`{"chat_id":123,"text":"%s"}`, messageText)

	stubNotification := wiremock.Post(wiremock.URLPathEqualTo(fmt.Sprintf("/bot%s/sendMessage", notifier.telegramBotApiKey))).
		WithBodyPattern(wiremock.EqualToJson(message)).
		WithHeader("Accept", wiremock.EqualTo("application/json")).
		WillReturnResponse(
			wiremock.NewResponse().
				WithStatus(http.StatusOK).
				WithHeader("Content-Type", "application/json").
				WithBody(`{"ok": true}`),
		)

	err = wiremockClient.StubFor(stubNotification)
	assert.NoError(t, err)

	command :=
		events.SQSMessage{
			Body: fmt.Sprintf(
				`
				{
					"AlarmName": "%s",
					"AlarmDescription": "%s",
					"NewStateValue": "OK",
					"NewStateReason": "Threshold Crossed: no datapoints were received for 3 periods and 3 missing datapoints were treated as [NonBreaching].",
					"StateChangeTime": "2023-12-30T07:48:51.322+0000"
				}
			`,
				healthAlarmName,
				alarmDescription,
			),
			MessageId: "1",
		}

	actualCommandsProcessed, err := notifier.Notify(events.SQSEvent{Records: []events.SQSMessage{command}})
	assert.NoError(t, err)

	expectedCommandsProcessed := events.SQSEventResponse{
		BatchItemFailures: nil,
	}
	assert.Equal(t, expectedCommandsProcessed, actualCommandsProcessed)

	verifyDownloadedCall, err := wiremockClient.Verify(stubNotification.Request(), 1)
	assert.NoError(t, err)
	assert.True(t, verifyDownloadedCall)
}
