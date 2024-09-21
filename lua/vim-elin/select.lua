local select = function(candidates, callback_vim_fn_name, optional_params)
  vim.ui.select(candidates, {prompt = "Select"}, function(choice)
    local args = vim.list_extend(optional_params, {choice})
    vim.fn[callback_vim_fn_name](args)
  end)
end

return {
  select = select,
}
