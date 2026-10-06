# Global Design System & Layout (Base Prompt)

*Paste this first into your AI code generator (v0, Claude, ChatGPT) to establish the global styling, Header, and Footer before generating specific pages.*

**Prompt:**
> "I want to build a modern, premium corporate website for an engineering and renewable energy company named 'Suffix Engineering Limited'. Create a base HTML, CSS, and vanilla JS template. I need EXACT 1:1 visual match with high fidelity and **great consistency of coding across all pages**.
> 
> **Architecture & Coding Consistency Requirements:**
> 1. **CSS Variables (Root):** Define all colors, typography, spacing, border-radius, and shadows in `:root`. Never hardcode colors or paddings in individual classes.
> 2. **Naming Convention:** Use strict BEM (Block Element Modifier) methodology for class names (e.g., `.card`, `.card__title`, `.card--highlight`) to ensure styles don't conflict across pages.
> 3. **Global Layout:** Create a `.container` class with a `max-width: 1280px`, `margin: 0 auto`, and standard horizontal padding. Use this consistently across all sections to ensure alignment.
> 4. **Reusable Components:** Create standard reusable utility classes for primary buttons (`.btn-primary`), secondary buttons (`.btn-secondary`), section titles (`.section-title`), and flex/grid layouts.
> 
> **Design System:**
> - **Primary Color:** Vibrant Green (`#00A651`) - use for buttons, icons, highlight text, and active states.
> - **Secondary Color:** Deep Navy Blue (`#0B1B36`) - use for the footer background, hero overlays, and primary headings.
> - **Backgrounds:** Pure White (`#FFFFFF`) and Light Gray (`#F5F7FA`) for alternating sections.
> - **Typography:** Use Google Fonts 'Inter' or 'Outfit'. Headings should be bold, modern, and dark navy (`#0B1B36`); body text should be clean and readable (size 16px, color `#4A5568`).
> - **UI Style:** Cards must have a white background, rounded corners (8px), a subtle box-shadow on hover (e.g., `0 10px 20px rgba(0,0,0,0.05)`), and 1px light gray borders (`#E2E8F0`). Use glassmorphism or smooth dark overlays for hero backgrounds.
> 
> **Components to generate in this step:**
> 1. **Header (Navbar):** Fixed at the top, pure white background, sticky with subtle shadow on scroll. Logo on the far left. Centered navigation links (Home, About Us, Our Business, Products, Projects, Services, Sustainability, Careers, Contact) with a green underline effect on hover/active. On the far right, a search icon and a solid green '.btn-primary' reading 'Get a Quote ->'.
> 2. **Footer:** Deep Navy background (`#0B1B36`), white text. Divided into columns: Logo with short description, 'Quick Links', 'Our Solutions', 'Connect With Us' (Address, Phone, Email with icons), and 'Follow Us' (Social media icons in green circles). A bottom border separating the copyright text and a 'back to top' arrow button.
> 
> Please output the `index.css` containing these global variables, utility classes, and the HTML shell with the Header and Footer."
