"""
SAP FSM Webhook Handler Microservice
Handles webhook calls from SAP FSM Mobile App
and integrates with Cumulocity IoT platform.
"""

import os
import logging
import sys
from flask import Flask, request, jsonify, redirect, make_response
from c8y_api import CumulocityApi
from werkzeug.exceptions import HTTPException
from flask_cors import CORS

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
app.config['JSON_SORT_KEYS'] = False


# Configuration
class Config:
    """Application configuration"""
    C8Y_BASEURL = os.getenv('C8Y_BASEURL', '')
    C8Y_TENANT = os.getenv('C8Y_TENANT', os.getenv('C8Y_BOOTSTRAP_TENANT', ''))
    C8Y_USER = os.getenv('C8Y_USER', os.getenv('C8Y_BOOTSTRAP_USER', ''))
    C8Y_PASSWORD = os.getenv('C8Y_PASSWORD', os.getenv('C8Y_BOOTSTRAP_PASSWORD', ''))

    # Tenant option configuration
    TENANT_OPTION_CATEGORY = "sap.fsm.mobile"
    TENANT_OPTION_KEY = "c8y-dashboard-url"

    @classmethod
    def is_configured(cls):
        """Check if all required configuration is present"""
        return all([
            cls.C8Y_BASEURL,
            cls.C8Y_TENANT,
            cls.C8Y_USER,
            cls.C8Y_PASSWORD
        ])


# Global C8Y API instance
c8y_api = None


def get_c8y_api():
    """Get or create Cumulocity API instance"""
    global c8y_api
    if c8y_api is None:
        if not Config.is_configured():
            logger.error("Cumulocity configuration is incomplete. Check environment variables.")
            raise RuntimeError("Microservice not properly configured")

        try:
            c8y_api = CumulocityApi(
                base_url=Config.C8Y_BASEURL,
                tenant_id=Config.C8Y_TENANT,
                username=Config.C8Y_USER,
                password=Config.C8Y_PASSWORD
            )
            logger.info(f"Connected to Cumulocity IoT at {Config.C8Y_BASEURL}")
        except Exception as e:
            logger.error(f"Failed to initialize Cumulocity API: {str(e)}")
            raise

    return c8y_api


def get_c8y_dashboard_url():
    """
    Retrieve Cumulocity Dashboard URL from tenant options

    Returns:
        str: The Cumulocity Dashboard URL

    Raises:
        ValueError: If tenant option is not configured
        RuntimeError: If API call fails
    """
    try:
        c8y = get_c8y_api()

        # Retrieve tenant option
        logger.info(f"Fetching tenant option: {Config.TENANT_OPTION_CATEGORY}.{Config.TENANT_OPTION_KEY}")

        # Use the tenant options API
        tenant_option = c8y.tenant_options.get(
            category=Config.TENANT_OPTION_CATEGORY,
            key=Config.TENANT_OPTION_KEY
        )

        if not tenant_option or not tenant_option.value:
            error_msg = (
                f"Tenant option '{Config.TENANT_OPTION_CATEGORY}.{Config.TENANT_OPTION_KEY}' "
                f"is not configured. Please configure the SAP FSM account ID in tenant options."
            )
            logger.error(error_msg)
            raise ValueError(error_msg)

        c8y_dashboard_url = tenant_option.value
        logger.info(f"Retrieved Cumulocity Dashboard URL: {c8y_dashboard_url}")
        return c8y_dashboard_url

    except ValueError:
        # Re-raise ValueError as-is
        raise
    except Exception as e:
        error_msg = f"Failed to retrieve tenant option: {str(e)}"
        logger.error(error_msg)
        raise RuntimeError(error_msg)


@app.route('/health', methods=['GET'])
def health_check():
    """
    Health check endpoint
    Returns the service status and configuration state
    """
    try:
        health_status = {
            "status": "UP",
            "service": "fsm-webhook-handler",
            "version": "1.0.0"
        }

        # Check if C8Y connection is available
        if Config.is_configured():
            try:
                get_c8y_api()
                health_status["cumulocity"] = "connected"
            except Exception as e:
                health_status["cumulocity"] = f"error: {str(e)}"
                health_status["status"] = "DEGRADED"
        else:
            health_status["cumulocity"] = "not configured"
            health_status["status"] = "DOWN"

        status_code = 200 if health_status["status"] == "UP" else 503
        return jsonify(health_status), status_code

    except Exception as e:
        logger.error(f"Health check failed: {str(e)}")
        return jsonify({
            "status": "DOWN",
            "error": str(e)
        }), 503

CORS(app, supports_credentials=True)

@app.route('/c8y/dashboard', methods=['GET', 'POST', 'OPTIONS'])
def fsm_mob_webhook_handler():
    # This gets 'https://tenant.cumulocity.com' from the browser's request
    public_host_row = request.headers.get('X-Forwarded-Host', request.host)

    public_host = public_host_row.rsplit(':', 1)[0] if ':' in public_host_row else public_host_row


    logger.info(f"Public host is  {public_host}")

    # 2. Get the protocol (https or http)
    protocol = request.headers.get('X-Forwarded-Proto', 'https')

    base_url = f"{protocol}://{public_host}"

    # Retrieve Cumulocity Dashboard URL rom tenant options
    try:
        c8y_dashboard_url = get_c8y_dashboard_url()
    except ValueError as e:
        # Configuration error - return 400
        return jsonify({
            "success": False,
            "error": str(e),
            "hint": f"Configure tenant option :  "
                    f"Category: '{Config.TENANT_OPTION_CATEGORY}', Key: '{Config.TENANT_OPTION_KEY}'"
        }), 400
    except RuntimeError as e:
        # API error - return 500
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500

    # 1. Handle Preflight (The browser's "check" call)
    if request.method == 'OPTIONS':
        response = make_response("", 204)  # 204 is better for OPTIONS
        origin = request.headers.get('Origin', '*')
        response.headers["Access-Control-Allow-Origin"] = origin
        response.headers["Access-Control-Allow-Methods"] = "POST, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
        response.headers["Access-Control-Allow-Credentials"] = "true"
        return response

    # 2. Handle the "After-Auth" GET request
    # If the browser retries as a GET after the popup, we should
    # redirect them to the dashboard immediately.
    if request.method == 'GET':
        try:
            #base_url = Config.C8Y_BASEURL.rstrip('/')
            full_url = base_url + c8y_dashboard_url
            return redirect(full_url, code=302)
        except Exception as e:
            return jsonify({"error": "Could not redirect after login", "details": str(e)}), 500

    """
    Handle webhook calls from FSM Mobile App

    Expected JSON payload:
    {
        "serviceOrderId": "12345",
        ... other FSM fields
    }

    Returns:
        JSON response with dashboard URL and success message
    """
    try:
        # Log incoming request
        logger.info(f"Received FSM Mobile App from {request.remote_addr}")

        # Validate content type
        if not request.is_json:
            logger.warning("Request content-type is not JSON")
            return jsonify({
                "success": False,
                "error": "Content-Type must be application/json"
            }), 400

        # Get JSON payload
        payload = request.get_json()
        logger.info(f"Payload received: {payload}")

        '''
        # Extract service order ID
        #service_order_id = payload.get('serviceOrderId')
        if not service_order_id:
            logger.error("Missing serviceOrderId in payload")
            return jsonify({
                "success": False,
                "error": "Missing required field 'serviceOrderId' in payload"
            }), 400

        logger.info(f"Processing service order: {service_order_id}")
        '''



        # Construct Cockpit dashboard URL
        #base_url = Config.C8Y_BASEURL.rstrip('/')
        dashboard_url = base_url + c8y_dashboard_url
        #(
        #    f"{base_url}/apps/cockpit/index.html#/fsm-integration"
        #    f"?accountId={account_id}&orderId={service_order_id}"

        #)

        logger.info(f"Generated dashboard URL: {dashboard_url}")

        # Log additional payload information for debugging
        if logger.isEnabledFor(logging.DEBUG):
            logger.debug(f"Full payload details: {payload}")

        # Return success response
        ''' response = {
            "success": True,
            "message": f"FSM Mobile App call processed successfully",
            "dashboardUrl": dashboard_url,
            "data": {
              #  "serviceOrderId": service_order_id,
              #  "accountId": account_id,
                "timestamp": payload.get('timestamp', 'N/A')
            }
        }
        '''
        logger.info("Successfully processed FSM Mobile App call")
        #return jsonify(response), 200
        #return redirect(dashboard_url, code=302)

        # 3. Create the Redirect response
        # code=303 is often better than 302 for forcing a GET after a POST
        #response = make_response(redirect(dashboard_url, code=303))
        html_redirector = f"""
                <!DOCTYPE html>
                <html>
                    <body onload="window.location.replace('{dashboard_url}')">
                        <div style="text-align:center;margin-top:50px;font-family:sans-serif;">
                            <p>Redirecting to Dashboard...</p>
                            <a href="{dashboard_url}">Click here if not redirected</a>
                        </div>
                    </body>
                </html>
                """
        response = make_response(html_redirector)
        origin = request.headers.get('Origin', '*')
        response.headers["Access-Control-Allow-Origin"] = origin
        response.headers["Access-Control-Allow-Credentials"] = "true"
        # Expose Location so the browser knows where the redirect is going
        #response.headers["Access-Control-Expose-Headers"] = "Location"
        response.headers["Content-Type"] = "text/html"

        return response

    except Exception as e:
        logger.error(f"Unexpected error processing FSM Mobile App call: {str(e)}", exc_info=True)
        return jsonify({
            "success": False,
            "error": "Internal server error occurred while processing FSM Mobile App call",
            "details": str(e)
        }), 500


@app.errorhandler(HTTPException)
def handle_http_exception(e):
    """Handle HTTP exceptions"""
    logger.warning(f"HTTP exception: {e.code} - {e.description}")
    return jsonify({
        "success": False,
        "error": e.description
    }), e.code


@app.errorhandler(Exception)
def handle_exception(e):
    """Handle unexpected exceptions"""
    logger.error(f"Unhandled exception: {str(e)}", exc_info=True)
    return jsonify({
        "success": False,
        "error": "An unexpected error occurred",
        "details": str(e)
    }), 500


@app.route('/', methods=['GET'])
def root():
    """Root endpoint with service information"""
    return jsonify({
        "service": "Cumulocity SAP FSM Mobile Webhook Handler",
        "version": "1.0.0",
        "status": "running",
        "endpoints": {
            "health": "/health",
            "webhook": "/c8y/dashboard (POST)"
        }
    }), 200


# Initialize C8Y connection on startup
@app.before_request
def before_first_request():
    """Initialize connections before first request"""
    if c8y_api is None:
        try:
            get_c8y_api()
        except Exception as e:
            logger.warning(f"Could not initialize C8Y API on startup: {str(e)}")


@app.route('/launcher', methods=['GET', 'POST', 'OPTIONS'])
def lanucher():
    # 1. Handle CORS Preflight (Crucial for FSM Mobile)
    if request.method == 'OPTIONS':
        response = make_response("", 204)
        response.headers["Access-Control-Allow-Origin"] = "*"
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
        return response


    # 3. Protocol Switch: Force the browser to GET the dashboard
    # Replace with your actual dashboard path
    dashboard_path = f"/apps/cockpit/index.html#/dashboard/"

    # We use 303 to tell FSM: "I received your POST, now go GET this page."
    response = make_response(redirect(dashboard_path, code=303))
    response.headers["Access-Control-Allow-Origin"] = "*"
    return response

    #return app.send_static_file('launcher.html')

if __name__ == '__main__':
    # For local development only
    logger.info("Starting FSM Mobile App Webhook Handler Microservice in development mode")
    logger.warning("WARNING: Development mode should not be used in production!")

    # Load .env file for local development
    try:
        from dotenv import load_dotenv

        load_dotenv()
        logger.info("Loaded environment variables from .env file")
    except ImportError:
        logger.warning("python-dotenv not available, skipping .env file loading")

    port = int(os.getenv('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True)

