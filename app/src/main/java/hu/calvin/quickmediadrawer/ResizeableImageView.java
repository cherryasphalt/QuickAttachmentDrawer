package hu.calvin.quickmediadrawer;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

public class ResizeableImageView extends ImageView {
    public ResizeableImageView(Context context) {
        super(context);
    }

    public ResizeableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResizeableImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getDrawable() != null) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = width * getDrawable().getIntrinsicHeight() / getDrawable().getIntrinsicWidth();
            setMeasuredDimension(width, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
