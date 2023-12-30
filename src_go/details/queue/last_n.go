package queue

import (
	"fmt"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/sqs"
)

// this is a test util. But it is accessible from production code. Figure out how to create shared test utils in go
func GetLastNCommands(svc *sqs.SQS, queueUrl string, numberOfMessages int) (lastNMessages []sqs.Message, err error) {

	var lastMessages []sqs.Message
	supposedlyHasMessages := true

	for {
		if !supposedlyHasMessages {
			break
		}
		var resp *sqs.ReceiveMessageOutput
		resp, err = svc.ReceiveMessage(&sqs.ReceiveMessageInput{
			QueueUrl:            &queueUrl,
			MaxNumberOfMessages: aws.Int64(10), // You can adjust this number
			VisibilityTimeout:   aws.Int64(10), // 30 seconds timeout for processing
			WaitTimeSeconds:     aws.Int64(0),  // Long polling
		})
		if err != nil {
			fmt.Printf("Failed to fetch message with error%v", err)
			return
		}

		if (len(resp.Messages)) == 0 {
			supposedlyHasMessages = false
			break
		}

		for _, message := range resp.Messages {
			_, err = svc.DeleteMessage(&sqs.DeleteMessageInput{
				QueueUrl:      &queueUrl,
				ReceiptHandle: message.ReceiptHandle,
			})
			if err != nil {
				fmt.Printf("Failed to delete message with error%v", err)
				return
			}
			if message != nil {
				lastMessages = append(lastMessages, *message)
			}
		}
	}

	if len(lastMessages) > numberOfMessages {
		lastNMessages = lastMessages[len(lastMessages)-numberOfMessages:]
	} else {
		lastNMessages = lastMessages
	}

	return
}
