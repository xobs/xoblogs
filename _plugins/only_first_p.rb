
module Jekyll
  module AssetFilter    
    
    def only_first_p(post)       
      output = post.split("</p>")[0]
      output << %{</p>}
      output
    end    
    
  end
end

Liquid::Template.register_filter(Jekyll::AssetFilter)
