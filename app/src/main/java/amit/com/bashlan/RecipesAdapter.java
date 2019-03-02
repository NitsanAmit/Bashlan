package amit.com.bashlan;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * 26-Feb-19
 * Created by Nitsa
 */
public class RecipesAdapter extends ArrayAdapter<Recipe> {

    private Context context;
    private List<Recipe> recipes = null;

    public RecipesAdapter(Context context, List<Recipe> recipes) {
        super(context, 0, recipes);
        this.context = context;
        this.recipes = recipes;
    }

    @Override
    public int getCount() {
        return recipes.size();
    }


    @Override
    public long getItemId(int position) {
        return recipes.indexOf(getItem(position));
    }


    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {

        final RecipeHolder holder;
        if (convertView == null) {
            holder = new RecipeHolder();
            LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
            convertView = inflater.inflate(R.layout.item_recipe, parent, false);
            holder.title = convertView.findViewById(R.id.txt_title);
            holder.ingredients = convertView.findViewById(R.id.text_ingredients);
            holder.thumbnail = convertView.findViewById(R.id.img_thumbnail);
            convertView.setTag(holder);
        } else {
            holder = (RecipeHolder) convertView.getTag();
        }

        final Recipe recipe = recipes.get(position);
        holder.title.setText(recipe.getTitle().trim());
        holder.ingredients.setText(recipe.getIngredients().trim());
        if(recipe.getThumbnail() == null || recipe.getThumbnail().equals("")){
            holder.thumbnail.setVisibility(View.GONE);
        }else{
            holder.thumbnail.setVisibility(View.VISIBLE);
            Glide
                    .with(context)
                    .load(recipe.getThumbnail())
                    .into(holder.thumbnail);
        }

        return convertView;
    }


    private class RecipeHolder {
        TextView title;
        TextView ingredients;
        ImageView thumbnail;
    }
}
