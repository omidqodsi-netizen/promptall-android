# PromptAll Android v3.8.0 smoke test

1. Install PromptAll Image Search 1.3.5+ and keep the existing completed image index.
2. Open Search; select an image and verify normal search starts immediately without another button.
3. Verify exact PromptAll image matches still work and aHash is included in the request.
4. Verify the AI card is visible directly below the selected-image panel even when normal results exist.
5. With VPN off in a restricted network, tap AI and verify a clear Gemini/VPN error appears without breaking normal results.
6. Turn VPN on and tap AI again; verify Gemini analysis runs from the phone.
7. Verify fallback models are attempted if the configured model fails.
8. Verify AI results are shown separately from normal results and are capped to verified candidates.
9. Verify changing only the person's face does not automatically reject an otherwise matching concept.
10. Verify generic portrait similarity alone does not pass the final AI verification.
11. If no verified result exists, verify the generated prompt button is immediately visible in the AI panel.
12. Tap generated prompt; verify Persian/English prompt appears in the same visible panel and can be copied.
13. Verify normal search never uploads raw image bytes to PromptAll.
14. Verify AI fallback image goes directly to Gemini, not to PromptAll.
15. Verify daily quota values can exceed 20 and display correctly.
16. Verify image sharing from Gallery/Telegram/browser still opens PromptAll Search and starts normal image search.
17. Open a normal result and an AI result; both must open the existing Prompt Detail screen.
18. Verify Favorites works from both normal and AI result cards.
19. Verify Back returns to the image-search results without losing the selected image.
20. Verify text search continues to work below image search.
21. Verify Home, Categories, Trending, Favorites and Prompt Detail regressions are absent.
22. Build signed APK/AAB with the same Bazaar signing key.

