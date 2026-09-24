# WHYTHIS

WHYTHIS is a search-first knowledge product built around a curated explanation graph.

## Product loop

Question -> Fast answer -> Mechanism -> Common mix-up -> Source trail -> Next question

The goal is not to imitate a chatbot. The goal is to help people understand something and continue learning.

## Architecture

- Frontend: dependency-light HTML/CSS/JavaScript
- Backend: Supabase Postgres + Row Level Security
- Hosting: GitHub Pages via GitHub Actions
- Public data: categories, published topics, daily lessons
- Subscriber capture: insert-only access with RLS

## Production database

Supabase project: WHYTHIS (rxxbercdedehtjfyppdb) in ap-south-1.

## Live site

Expected GitHub Pages URL:
https://jaydevare808.github.io/JD/whythis/

## Maintenance

Update the website files under /whythis and the Supabase schema/data together. Any push to main that changes /whythis triggers the GitHub Pages deployment workflow.
