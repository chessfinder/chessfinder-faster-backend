package main

type Notification struct {
	ChatId          int64  `json:"chat_id"`
	Text            string `json:"text"`
	MessageTheardId *int64 `json:"message_thread_id,omitempty"`
}

type NotificationResult struct {
	Ok bool `json:"ok"`
}
