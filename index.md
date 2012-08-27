---
layout: default
title: Xobs' Blog
tagline: Notes on various projects
---

<!--
<div id="post" class="grid_8 alpha content">
</div>
<div class="clear"></div>
-->

{% assign first_post = site.posts.first %}
<div id="post" class="grid_6 alpha content">
  <h2><a href="{{ first_post.url }}">
    newest post: {{ first_post.title }}
  </a></h2>
  {{ first_post.content | only_first_p }}
  <a id="more" href="{{ first_post.url }}">Read More &raquo;</a>
</div>

<div class="grid_2 omega links">
  <h3 class="link_title" id="recent">Recent posts</h3>
  {% for post in site.posts limit: 7 %}
    <a href="{{ post.url }}">{{ post.title }}</a>
  {% endfor %}

  <h3 class="link_title" id="resources">Resources</h3>
    <a href="http://gitready.com">Git Ready</a>
    <a href="http://http://git.kernel.org/?p=linux/kernel/git/next/linux-next.git;a=summary">Linux-Next git repo</a>
    <a href="http://www.freescale.com/webapp/sps/site/prod_summary.jsp?code=i.MX233">i.MX233 vendor page</a>
</div>

