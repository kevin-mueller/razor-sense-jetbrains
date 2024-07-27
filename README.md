# Razor Sense
This plugin aims to improve your experience with Blazor in JetBrains Rider.

## Currently implemented

### Better Css Class Completion
The existing completion for class attributes like this is pretty lackluster.
```html 
<Component Class="my-class">
 ...
</Component>
```
It only works if the file you're editing is in a web project, and only classes from css files in the `wwwroot` folder of that project are considered.

With this plugin these issues are fixed. Even css classes from external libraries, such as [MudBlazor](https://mudblazor.com/), or referenced files via `<link href="..." />` are considered for completion.


### ... More?
If you have any ideas to improve this plugin further and make the experience of working with Blazor even better, feel free to create an issue, or open a PR yourself. :)


<a href="https://www.buymeacoffee.com/KevinMueller" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>
