package org.example.apimywebsite.util;

// Local-only image paths for Demo Mode's seeded dataset (profile pictures + post images).
// Deliberately separate from Constants.java (real users' Cloudinary-hosted default avatars,
// completely unchanged by Demo Mode) - these are relative frontend paths, served from the React
// app's own `public/demo-assets/` folder, never from Cloudinary or any other external host. A
// relative path like "/demo-assets/profiles/demo-user.jpg" resolves against the document's own
// origin in the browser, i.e. wherever the frontend itself is hosted - not against the backend's
// API_BASE_URL - so rendering it never issues a request to this backend or to Cloudinary.
// Extensions below (.jpeg for profiles, .png for posts) match the actual files placed under
// Faceboard-frontend/public/demo-assets/ - deliberately NOT ".jpg" for either, since renaming/
// re-encoding the already-provided images was explicitly out of scope; the code was aligned to
// the files, not the other way around.
public class DemoAssets {
    public static final String PROFILE_DEMO_USER = "/demo-assets/profiles/demo-user.jpeg";
    public static final String PROFILE_ALEX = "/demo-assets/profiles/alex-rivera.jpeg";
    public static final String PROFILE_JAMIE = "/demo-assets/profiles/jamie-chen.jpeg";
    public static final String PROFILE_SAM = "/demo-assets/profiles/sam-okafor.jpeg";

    public static final String POST_CITY_NIGHT = "/demo-assets/posts/city-night.png";
    public static final String POST_COFFEE_WORKSPACE = "/demo-assets/posts/coffee-workspace.png";
    public static final String POST_HIKING_VIEW = "/demo-assets/posts/hiking-view.png";
    public static final String POST_TRAVEL_STREET = "/demo-assets/posts/travel-street.png";
    public static final String POST_LAPTOP_PROJECT = "/demo-assets/posts/laptop-project.png";
}
