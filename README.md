# chessfinder.org Documentation

## 1. Introduction

### Project Overview
chessfinder.org is a unique tool designed to help chess players find their past games based on partial board positions. It integrates seamlessly with chess.com APIs and offers a user-friendly interface for searching games.

### Motivation
This project was born out of a personal challenge I faced in locating a specific game from a vast archive on chess.com. Recognizing that other players might face similar difficulties, I developed chessfinder.org to address this need.

### Target Audience
The application is intended for chess enthusiasts, professional players, and analysts who wish to retrieve specific games from their playing history without remembering the exact details.

## 2. PPN (Partial Position Notation)

### Definition
PPN (Partial Position Notation) is a format designed to represent partial or uncertain chess positions on a chessboard. It allows for the expression of specific known elements of a board setup while accommodating uncertainties or unknown aspects.

### PPN Structure
- The notation starts from the A8 square (top-left corner of the board) and proceeds from left to right, moving downward rank by rank.
- Each rank (row) of the board is represented, separated by a slash (`/`).
- The notation uses the following symbols to represent pieces:
  - **Pawns:** `p` for black, `P` for white.
  - **Knights:** `n` for black, `N` for white.
  - **Bishops:** `b` for black, `B` for white.
  - **Rooks:** `r` for black, `R` for white.
  - **Queens:** `q` for black, `Q` for white.
  - **Kings:** `k` for black, `K` for white.
  - **Question Mark (`?`):** Indicates an unknown state of a square – it could be either occupied or empty.
  - **Hyphen (`-`):** Represents a square that is certainly empty.
  - **Occupied Square Indicators:**
    - **`o`:** Occupied by an unknown black piece.
    - **`O`:** Occupied by an unknown white piece.
    - **`0`:** Occupied by a piece of unknown color.

### Comparison with FEN
For those familiar with Forsyth-Edwards Notation (FEN), PPN differs in its ability to represent uncertainty and partial information. Unlike FEN, which requires a precise description of each square, PPN allows for ambiguous states and does not use numbers to represent consecutive empty squares. Empty squares are marked by hyphens (`-`).

### Example of PPN Usage

Consider the PPN: `??----rk/?????Npp/????????/????????/????????/????????/????????/????????`. ![Smothered Mate PPN](/doc/smothered_mate_partial_position_1.png)

This notation describes a specific configuration of a chessboard where:
This notation describes a specific configuration of a chessboard where:
- The first two squares of the eighth rank (A8 and B8) are unknown (`??`), followed by four definitively empty squares (`----`), ending with a black rook (`r`) and king (`k`) on G8 and H8.
- On the seventh rank (A7 to H7), the state of the first five squares is unknown (`?????`), the sixth square (F7) is occupied by a white knight (`N`), and the last two squares (G7 and H7) are occupied by black pawns (`pp`).
- The state of the rest of the board (ranks 1-6) is entirely unknown, as indicated by rows of question marks (`?`).


This PPN is useful for searching games where you remember a specific arrangement involving an empty middle section on the top rank, a black rook and king at the end of the top rank, a white knight on the seventh rank, and two black pawns at the end of the seventh rank, but the rest of the board details are unclear or forgotten.

These two games will match the PPN above:
![Smothered Mate 1](/doc/smothered_mate_exact_position_1.png)
![Smothered Mate 2](/doc/smothered_mate_exact_position_1.png)

Let's now consider this PPN: `??----rk/?????Npp/??????--/????????/????????/????????/????????/????????`
![Smothered Mate PPN 2](/doc/smothered_mate_partial_position_2.png)

This time we explicitly ask that the squares G6 and H6 be free, therefore the second game won't match since it has a black knight on G6.

## 3. APIs and Architectural Diagram

The application consists of two flows: downloading games from chess.com and searching for games based on PPN. The following sections describe the API endpoints and the architectural diagram.

### Downloading Games from chess.com
![Downloading games](/doc/download_games.png)

### Searching for Games Based on PPN
![Searching games](/doc/search_games.png)

## 4. Running Tests Locally

### Prerequisites
- JDK 17
- SBT 1.8.2 
- Docker
- AWS CLI
- SAM CLI
- SAM Local (from LocalStack)

### Test Environment Setup
1. Spin up a local DynamoDB, SQS, WireMock and vizualization tools: `docker compose -f .dev/docker-compose_local.yaml --env-file .dev/.env up`
2. Create the necessary AWS resources (tables and queues) and deploy the application locally using SAM Local. Example commands for creating the resources and deploying the application are as follows:
- `aws --endpoint-url http://localhost:4566  s3api create-bucket --bucket chessfinder`
- `samlocal deploy --template-file .infrastructure/db.yaml --stack-name chessfinder_dynamodb --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder --parameter-overrides TheStackName=chessfinder_dynamodb`
- `samlocal deploy --template-file .infrastructure/queue.yaml --stack-name chessfinder_sqs --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder --parameter-overrides TheStackName=chessfinder_sqs`.

### Executing Tests

1. Go to each go module and run `go test ./... -v` to run the tests.
2. For Scala tests, run the following commands in the root of the project:
- `sbt test`
- `sbt core/test`. 
