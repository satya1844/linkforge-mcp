import os
import httpx
from fastmcp import FastMCP
from dotenv import load_dotenv

load_dotenv()

BASE_URL = os.getenv("LINKFORGE_BASE_URL", "http://localhost:8080")
API_KEY  = os.getenv("LINKFORGE_API_KEY", "")

mcp = FastMCP("LinkForge")

def headers():
    return {
        "X-API-Key": API_KEY,
        "Content-Type": "application/json"
    }


@mcp.tool()
def shorten_url(
        original_url: str,
        custom_alias: str = None,
        expires_in_days: int = None
) -> dict:
    """Shorten a long URL. Optionally set a custom alias and expiry in days."""
    payload = {"originalUrl": original_url}
    if custom_alias:
        payload["customAlias"] = custom_alias
    if expires_in_days:
        payload["expiresInDays"] = expires_in_days

    with httpx.Client() as client:
        resp = client.post(f"{BASE_URL}/urls", json=payload, headers=headers())

    if resp.status_code == 201:
        data = resp.json()
        return {
            "shortCode": data["shortCode"],
            "shortUrl":  data["shortUrl"],
            "originalUrl": data["originalUrl"],
            "expiresAt": data.get("expiresAt")
        }
    elif resp.status_code == 409:
        return {"error": f"Alias '{custom_alias}' is already taken"}
    elif resp.status_code == 400:
        return {"error": f"Invalid input: {resp.json().get('message', resp.text)}"}
    else:
        return {"error": f"Unexpected error: {resp.status_code} — {resp.text}"}


@mcp.tool()
def get_analytics(short_code: str) -> dict:
    """Get click analytics for a short link."""
    with httpx.Client() as client:
        resp = client.get(
            f"{BASE_URL}/urls/{short_code}/analytics",
            headers=headers()
        )

    if resp.status_code == 200:
        return resp.json()
    elif resp.status_code == 404:
        return {"error": f"Short code '{short_code}' not found"}
    else:
        return {"error": f"Unexpected error: {resp.status_code}"}


@mcp.tool()
def list_links() -> dict:
    """List all short links for the current API key."""
    with httpx.Client() as client:
        resp = client.get(f"{BASE_URL}/urls", headers=headers())

    if resp.status_code == 200:
        return {"links": resp.json()}
    else:
        return {"error": f"Failed to fetch links: {resp.status_code}"}


@mcp.tool()
def delete_link(short_code: str) -> dict:
    """Delete a short link by its short code."""
    with httpx.Client() as client:
        resp = client.delete(
            f"{BASE_URL}/urls/{short_code}",
            headers=headers()
        )

    if resp.status_code == 204:
        return {"success": True, "message": f"Link '{short_code}' deleted"}
    elif resp.status_code == 404:
        return {"error": f"Short code '{short_code}' not found"}
    else:
        return {"error": f"Unexpected error: {resp.status_code}"}


@mcp.tool()
def check_link_health(short_code: str) -> dict:
    """Check if the original URL behind a short link is still reachable."""
    # First get the original URL
    with httpx.Client() as client:
        analytics = client.get(
            f"{BASE_URL}/urls/{short_code}/analytics",
            headers=headers()
        )

    if analytics.status_code != 200:
        return {"error": f"Short code '{short_code}' not found"}

    original_url = analytics.json().get("originalUrl")

    # Then HEAD request to check if it's alive
    try:
        with httpx.Client(follow_redirects=True, timeout=10) as client:
            resp = client.head(original_url)
        return {
            "shortCode":   short_code,
            "originalUrl": original_url,
            "status":      resp.status_code,
            "healthy":     resp.status_code < 400
        }
    except httpx.RequestError as e:
        return {
            "shortCode":   short_code,
            "originalUrl": original_url,
            "healthy":     False,
            "error":       str(e)
        }





if __name__ == "__main__":
    mcp.run()