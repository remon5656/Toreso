# Toreso

Toreso is a project designed to link application data with Point of Sale (POS) data from stores to create additional value.

## Features

-   **Item Locator**: Search for the location of items you want.
-   **Payment Functionality**: A built-in payment system.

## Getting Started

Follow these instructions to get a copy of the project up and running on your local machine.

### Prerequisites

This project requires API keys to function properly. You will need to obtain your own keys and configure them as follows:

1.  **Backend Configuration:**
    Create a `config.env` file in the `backend/` directory and add your API keys:
    ```
    GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
    JAN_APP_ID="YOUR_JAN_APP_ID"
    ```

2.  **Mobile Configuration:**
    In the `mobile/janmapping/app/src/main/res/values/strings.xml` file, add your Google Maps API key:
    ```xml
    <resources>
        <string name="google_maps_key">YOUR_GOOGLE_MAPS_KEY</string>
    </resources>
    ```
    *Note: Make sure to replace `"YOUR_..._KEY"` with your actual API keys.*

### Installation

1.  Start the backend server.
2.  On the same machine, launch the application using an Android emulator.

## Technology Stack

-   TypeScript
-   Kotlin
-   SQL

## License

The license for this project has not yet been determined.