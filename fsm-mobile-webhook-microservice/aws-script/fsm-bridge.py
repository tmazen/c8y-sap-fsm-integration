import requests
import json
from flask import Flask, request, jsonify, make_response

app = Flask(__name__)

# --- CONFIGURATION ---
# Your Cumulocity URL
C8Y_URL = "https://<tenant domain>/service/c8y-fsm-mob-ms-py/c8y/dashboard"

# Your Cumulocity Credentials (tenant/user:password)
#C8Y_CREDENTIALS = "your_tenant/your_username:your_password"

# Encode the credentials for the Basic Auth header
encoded_auth = "Basic <Encoded Authorization>"
AUTH_HEADER = {"Authorization": f"{encoded_auth}"}


@app.route('/fsm-bridge', methods=['POST', 'OPTIONS'])
def proxy_handler():
    # 1. Handle CORS for FSM Mobile
    if request.method == 'OPTIONS':
        response = make_response("", 204)
        response.headers["Access-Control-Allow-Origin"] = "*"
        response.headers["Access-Control-Allow-Headers"] = "*"
        response.headers["Access-Control-Allow-Methods"] = "POST, OPTIONS"
        return response

    # Print Headers
    print("\n--- [DEBUG] Incoming FSM Mobile Headers ---")
    # request.headers is a MultiDict; converting to dict for pretty printing
    headers_dict = dict(request.headers)
    print(json.dumps(headers_dict, indent=4))
    print("-------------------------------------------\n")

    # 2. Capture the incoming data from FSM
    fsm_data = request.get_json(silent=True) or {}

    if not fsm_data:
        return jsonify({"error": "Invalid JSON payload"}), 400

    print(f"Forwarding FSM Data: {fsm_data}")

    try:
        # 3. FORWARD the request to Cumulocity WITH the AUTH header
        # We use requests.post to send the data to the real microservice
        response = requests.post(
            C8Y_URL,
            json=fsm_data,
            headers=AUTH_HEADER,
            timeout=15
        )

        # 4. Return the result back to FSM
        # If your microservice returns a 303 Redirect, we pass that through
        proxy_response = make_response(response.content, response.status_code)

        # Copy important headers back (like Location for redirects)
       # if 'Location' in response.headers:
        #    proxy_response.headers['Location'] = response.headers['Location']

       # proxy_response.headers["Access-Control-Allow-Origin"] = "*"
        return proxy_response

    except requests.exceptions.Timeout:
        return jsonify({"error": "Cumulocity connection timed out"}), 504
    except requests.exceptions.ConnectionError:
        return jsonify({"error": "Could not connect to Cumulocity"}), 502
    except requests.exceptions.RequestException as e:
        return jsonify({"error": "General Request Error", "details": str(e)}), 500
    except Exception as e:
        return jsonify({"error": "Proxy failed", "details": str(e)}), 500


@app.errorhandler(500)
def handle_500(e):
    return jsonify({"error": "Internal Server Error", "message": "Something went wrong on our end"}), 500

if __name__ == '__main__':
    # Run on port 8080 (or whatever your host provides)
    app.run(host='0.0.0.0', port=8088)