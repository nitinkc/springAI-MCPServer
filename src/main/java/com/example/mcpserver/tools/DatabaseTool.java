package com.example.mcpserver.tools;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database Tool - Exposes database query capabilities to AI agents.
 * <p>
 * This tool demonstrates how to give an LLM safe, controlled access to your database through
 * well-defined operations.
 */
@Component
public class DatabaseTool {

  private final JdbcTemplate jdbcTemplate;

  public DatabaseTool(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Query products from the database.
   *
   * @param category Optional category filter (e.g., "Electronics", "Books")
   * @param maxPrice Optional maximum price filter
   * @return List of products matching the criteria
   */
  @Tool(description = "Search for products in the database. Can filter by category and maximum price. Returns product details including name, price, and stock status.")
  public List<Map<String, Object>> queryProducts(
      @ToolParam(description = "Category filter (e.g., 'Electronics', 'Books')") String category,
      @ToolParam(description = "Maximum price filter") Double maxPrice) {
    StringBuilder sql = new StringBuilder(
        "SELECT id, name, category, price, stock_quantity FROM products WHERE 1=1");

    if (category != null && !category.isBlank()) {
      sql.append(" AND LOWER(category) = LOWER('").append(category).append("')");
    }
    if (maxPrice != null) {
      sql.append(" AND price <= ").append(maxPrice);
    }
    sql.append(" ORDER BY name LIMIT 50");

    return jdbcTemplate.queryForList(sql.toString());
  }

  /**
   * Get inventory status for a specific product.
   *
   * @param productId The product ID to check
   * @return Inventory details including stock level and reorder status
   */
  @Tool(description = "Check the inventory status for a specific product by ID. Returns current stock quantity and whether reorder is needed.")
  public Map<String, Object> checkInventory(
      @ToolParam(description = "The product ID to check") Long productId) {
    String sql = """
        SELECT id, name, stock_quantity, 
               CASE WHEN stock_quantity < 10 THEN true ELSE false END as needs_reorder
        FROM products 
        WHERE id = ?
        """;

    List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, productId);
    if (results.isEmpty()) {
      return Map.of("error", "Product not found", "productId", productId);
    }
    return results.get(0);
  }

  /**
   * Get sales summary statistics.
   *
   * @return Summary statistics about products and inventory
   */
  @Tool(description = "Get a summary of sales statistics including total products, categories, and inventory value.")
  public Map<String, Object> getSalesSummary() {
    String sql = """
        SELECT 
            COUNT(*) as total_products,
            COUNT(DISTINCT category) as total_categories,
            SUM(price * stock_quantity) as total_inventory_value,
            AVG(price) as average_price
        FROM products
        """;

    return jdbcTemplate.queryForMap(sql);
  }
}
