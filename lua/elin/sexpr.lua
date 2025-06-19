local get_node_from_cursor_position = function(cursor_row, cursor_col)
    local parser = vim.treesitter.get_parser(0, vim.api.nvim_buf_get_option(0, 'filetype'))
    local tree = parser:parse()[1]
    return tree:root():named_descendant_for_range(cursor_row, cursor_col, cursor_row, cursor_col)
end

local function get_code(node)
    local node_type = node:type()
    local start_row, start_col, end_row, end_col = node:range()
    local node_text = vim.api.nvim_buf_get_lines(0, start_row, end_row + 1, false)
    node_text[1] = string.sub(node_text[1], start_col + 1)

    node_text[end_row - start_row + 1] = (start_row == end_row)
        and string.sub(node_text[end_row - start_row + 1], 1, end_col - start_col)
        or string.sub(node_text[end_row - start_row + 1], 1, end_col)

    return { code = table.concat(node_text, "\n"); lnum = start_row + 1; col = start_col + 1 }
end

local function get_range(node)
    local start_row, start_col, end_row, end_col = node:range()
    return { start_row = start_row, start_col = start_col, end_row = end_row, end_col = end_col }
end

local function find_list_lit(node)
    while (node and node:type() ~= 'list_lit') do
        node = node:parent()
    end
    return node
end

local function find_top_list_lit(node)
    while (node:parent():type() ~= "source") do
        node = node:parent()
    end

    return node
end

local get_top_list_node = function(cursor_row, cursor_col)
    local node = get_node_from_cursor_position(cursor_row, cursor_col)
    return node and find_top_list_lit(node) or nil
end

local get_list_node = function(cursor_row, cursor_col)
    local node = get_node_from_cursor_position(cursor_row, cursor_col)
    return node and find_list_lit(node) or nil
end

local get_expr_node = function(cursor_row, cursor_col)
    return get_node_from_cursor_position(cursor_row, cursor_col)
end



local get_top_list = function(cursor_row, cursor_col)
    local top_node = get_top_list_node(cursor_row, cursor_col)
    return top_node and get_code(top_node) or nil
end

local get_list = function(cursor_row, cursor_col)
    local list_node = get_list_node(cursor_row, cursor_col)
    return list_node and get_code(list_node) or nil
end

local get_expr = function(cursor_row, cursor_col)
    local node = get_expr_node(cursor_row, cursor_col)
    return node and get_code(node) or nil
end

local get_top_list_range = function(cursor_row, cursor_col)
    local top_node = get_top_list_node(cursor_row, cursor_col)
    return top_node and get_range(top_node) or nil
end

local get_list_range = function(cursor_row, cursor_col)
    local list_node = get_list_node(cursor_row, cursor_col)
    return list_node and get_range(list_node) or nil
end

local get_expr_range = function(cursor_row, cursor_col)
    local node = get_expr_node(cursor_row, cursor_col)
    return node and get_range(node) or nil
end

return {
    get_top_list = get_top_list,
    get_list = get_list,
    get_expr = get_expr,

    get_top_list_range = get_top_list_range,
    get_list_range = get_list_range,
    get_expr_range = get_expr_range,
}

