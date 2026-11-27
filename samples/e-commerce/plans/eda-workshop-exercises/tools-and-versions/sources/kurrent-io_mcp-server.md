# KurrentDB MCP Server
 This is a simple MCP server to help you explore data and prototype projections faster on top of KurrentDB.

## Recommended Usage
- Claude Desktop
- Sequential Thinking MCP for complex tasks

## Installation

### KurrentDB Setup
You need to enable --run-projections=all and --start-standard-projections on KurrentDB
The $streams stream is used to look for available streams.

### Python Dependencies
Ensure the packages in `requirements.txt` are installed using pip

### OS Dependencies
Ensure the `vu` package is installed on your machine where you will be running the MCP Server

For Mac: `brew install uv`

### MCP Client Setup (VS Code)

```json
{
    "servers": {
      "KurrentDB": {
        "type": "stdio",
        "command": "uv",
            "args": [
                "--directory",
                "path to mcp-server folder",
                "run",
                "server.py"
            ],
	    "env": {
             "KURRENTDB_CONNECTION_STRING": "insert kurrentdb connection here"
        }
      }
    }
  }
```
This configuration file should work in VS Code (.vscode/mcp.json).

### MCP Client Setup (Claude)

```json
{
    "mcpServers": {
      "KurrentDB": {
        "type": "stdio",
        "command": "uv",
            "args": [
                "--directory",
                "path to mcp-server folder",
                "run",
                "server.py"
            ],
	    "env": {
             "KURRENTDB_CONNECTION_STRING": "insert kurrentdb connection here"
        }
      }
    }
  }
```
This configuration file should work in Claude Desktop (https://modelcontextprotocol.io/quickstart/user).

### MCP Client Setup (Cursor or Windsurf)

```json
{
  "mcpServers": {
    "kurrentdb": {
      "command": "python",
      "args": ["path to mcp-server folder\\server.py"],
      "env": {
             "KURRENTDB_CONNECTION_STRING": "insert kurrentdb connection here" 
         }
    }
  }
}
```
This configuration file should work in Cursor (\.cursor\mcp.json) and Windsurf (\.codeium\windsurf\mcp_config.json).


## Overview
This MCP server is designed to make stream data available to the MCP client. 
It provides a simple interface for querying and retrieving stream data.
It can also create, test and debug projections.

Access control is done using the KurrentDB connection string provided at configuration time as an environment variable.

## Components

### Tools
The servers exposes 8 tool calls:
1. `read_stream`
2. `list_streams`
3. `build_projection`
4. `create_projection`
5. `update_projection`
6. `test_projection`
7. `write_events_to_stream`
8. `get_projections_status`

### Configuration

- ConnectionString: This is a KurrentDB connection string which includes user credentials. Depending on the client being used, this can come from environment variable or a JSON Configuration file like in Claude Desktop's case.

# Usage Documentation

## Available Tools

### 1. Stream Operations

#### `read_stream`
Reads events from a specific stream in KurrentDB.

**Parameters:**
- `stream` (required): Stream name to read from
- `backwards` (optional, default: false): Read direction - true for newest first, false for oldest first
- `limit` (optional, default: 10): Number of events to return

**Sample Prompts:**
- "Read the last 5 events from the 'orders' stream"
- "Show me the first 20 events from the user-activity stream"
- "Get all events from the inventory stream, reading backwards"

**Example Usage:**
```
Tool: read_stream
Parameters:
- stream: "orders"
- backwards: true
- limit: 5
```

#### `write_events_to_stream`
Writes new events to a stream in KurrentDB.

**Parameters:**
- `stream` (required): Name of the stream to write to
- `data` (required): JSON object containing the event data
- `event_type` (required): Type/category of the event
- `metadata` (required): JSON object with additional event information

**Sample Prompts:**
- "Add a new order event to the orders stream with customer ID 123"
- "Record a user login event with timestamp and IP address"
- "Create a product update event in the inventory stream"

**Example Usage:**
```
Tool: write_events_to_stream
Parameters:
- stream: "orders"
- data: {"orderId": "ORD-001", "customerId": 123, "amount": 99.99}
- event_type: "OrderCreated"
- metadata: {"timestamp": "2025-05-19T10:00:00Z", "source": "web"}
```

#### `list_streams`
Lists all available streams in the KurrentDB database.

**Parameters:**
- `limit` (optional, default: 100): Number of streams to return
- `read_backwards` (optional, default: true): Read direction for the $streams stream

**Sample Prompts:**
- "Show me all streams in the database"
- "List the first 10 streams"
- "What streams are available in KurrentDB?"

**Example Usage:**
```
Tool: list_streams
Parameters:
- limit: 20
- read_backwards: true
```

### 2. Projection Operations

Projections in KurrentDB are computed views that process events from streams to create queryable data structures.

#### `build_projection`
Uses AI assistance to build a projection based on your requirements.

**Parameters:**
- `user_prompt` (required): Description of what the projection should do

**Sample Prompts:**
- "Create a projection that counts total orders by customer"
- "Build a projection showing daily revenue totals"
- "I need a projection that tracks inventory levels in real-time"

**Example Usage:**
```
Tool: build_projection
Parameters:
- user_prompt: "Create a projection that aggregates order totals by day and calculates running totals"
```

#### `create_projection`
Creates a projection in KurrentDB using provided code.

**Parameters:**
- `projection_name` (required): Name for the projection
- `code` (required): Generated projection code

**Sample Prompts:**
- "Create the customer analytics projection with the generated code"
- "Deploy this order summary projection to KurrentDB"

**Note:** Client normally always asks the user for confirmation before creating a projection.

#### `update_projection`
Updates an existing projection with new code.

**Parameters:**
- `projection_name` (required): Name of the projection to update
- `code` (required): Updated projection code

**Sample Prompts:**
- "Update the sales projection to include tax calculations"
- "Modify the user analytics projection to track more metrics"

#### `get_projections_status`
Retrieves status and statistics for a specific projection.

**Parameters:**
- `projection_name` (required): Name of the projection

**Sample Prompts:**
- "Check the status of the sales projection"
- "Show me statistics for the user-analytics projection"
- "Is the inventory projection running correctly?"

#### `test_projection`
Writes test events to a projection to verify its functionality. Verification is done by reading the streams emitted or the state of the projection.

**Sample Prompts:**
- "Test the order-analytics-projection with sample data"

**Parameters:**
- `projection_name` (required): Name of the projection to test

**Sample Prompts:**
- "How can I test the order analytics projection?"
- "Give me testing guidelines for the customer segmentation projection"


## Sample Events
Modern LLMs can generate sample events for various use cases on their given enough information.

### Order Event
```json
{
  "data": {
    "orderId": "ORD-12345",
    "customerId": "CUST-789",
    "items": [
      {"productId": "PROD-001", "quantity": 2, "price": 29.99}
    ],
    "total": 59.98
  },
  "event_type": "OrderCreated",
  "metadata": {
    "timestamp": "2025-05-19T14:30:00Z",
    "source": "ecommerce-api",
    "correlationId": "corr-123"
  }
}
```

### User Activity Event
```json
{
  "data": {
    "userId": "USER-456",
    "action": "page_view",
    "page": "/products/electronics",
    "sessionId": "sess-789"
  },
  "event_type": "UserActivity",
  "metadata": {
    "timestamp": "2025-05-19T14:35:00Z",
    "userAgent": "Mozilla/5.0...",
    "ipAddress": "192.168.1.100"
  }
}
