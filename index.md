---
layout: default
title: Xobs' Blog
tagline: Notes on various projects
---

{% assign first_post = site.posts.first %}
<div id="post" class="grid_6 alpha content">
  <h2><a href="{{ first_post.url }}">
    newest post: {{ first_post.title }}
  </a></h2>
  {{ first_post.content | only_first_p }}
  <a id="more" href="{{ first_post.url }}">Read More &raquo;</a>
</div>
<div class="grid_2 omega">
  <h3 id="recent">Recent posts</h3>
  <ul class="recent">
  {% for post in site.posts limit: 7 %}
    <li><a href="{{ post.url }}">{{ post.title }}</a></li>
  {% endfor %}
  </ul>
</div>
